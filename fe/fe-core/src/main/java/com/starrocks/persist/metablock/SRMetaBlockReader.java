// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package com.starrocks.persist.metablock;

import com.starrocks.common.io.Text;
import com.starrocks.persist.gson.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * Load object from input stream as the following format.
 *
 * +------------------+
 * |     header       | {"numJson": 10, "name": "AuthenticationManager"}
 * +------------------+
 * |     Json 1       |
 * +------------------+
 * |     Json 2       |
 * +------------------+
 * |      ...         |
 * +------------------+
 * |     Json 10      |
 * +------------------+
 * |      footer      | {"checksum": xxx}
 * +------------------+
 *
 * Usage see com.starrocks.persist.metablock.SRMetaBlockTest#testSimple()
 */
public class SRMetaBlockReader {
    private static final Logger LOG = LogManager.getLogger(SRMetaBlockReader.class);
    private CheckedInputStream checkedInputStream;
    private SRMetaBlockHeader header;
    private String name;
    private int numJsonReaded;

    public SRMetaBlockReader(DataInputStream dis, String name) {
        this.checkedInputStream = new CheckedInputStream(dis, new CRC32());
        this.name = name;
        this.header = null;
        this.numJsonReaded = 0;
    }

    private String readJsonText() throws IOException, SRMetaBlockException, SRMetaBlockEOFException {
        if (numJsonReaded == 0) {
            // read header and check for name
            String s = Text.readStringWithChecksum(checkedInputStream);
            header = GsonUtils.GSON.fromJson(s, SRMetaBlockHeader.class);
            if (! header.getName().equals(name)) {
                throw new SRMetaBlockException(
                        "Invalid meta block header, expect " + header.getName() + " actual " + name);
            }
        } else if (numJsonReaded >= header.getNumJson()) {
            throw new SRMetaBlockEOFException(String.format(
                    "Read json more than expect: %d >= %d", numJsonReaded, header.getNumJson()));
        }
        String s = Text.readStringWithChecksum(checkedInputStream);
        numJsonReaded += 1;
        return s;
    }

    public Object readJson(Class<?> returnClass) throws IOException, SRMetaBlockException, SRMetaBlockEOFException {
        return GsonUtils.GSON.fromJson(readJsonText(), returnClass);
    }

    public Object readJson(Type returnType) throws IOException, SRMetaBlockException, SRMetaBlockEOFException {
        return GsonUtils.GSON.fromJson(readJsonText(), returnType);
    }

    public void close() throws IOException, SRMetaBlockException {
        if (header == null) {
            LOG.warn("do nothing and quit.");
            return;
        }
        if (numJsonReaded < header.getNumJson()) {
            // discard the rest of data for compatibility
            // normally it's because this FE has just rollbacked from a higher version that would produce more metadata
            int rest = header.getNumJson() - numJsonReaded;
            LOG.warn("Meta block for {} read {} json < total {} json, will skip the rest {} json",
                    header.getName(), numJsonReaded, header.getNumJson(), rest);
            for (int i = 0; i != rest; ++ i) {
                LOG.warn("skip {} json: {}", i, Text.readStringWithChecksum(checkedInputStream));
            }
        }

        // 1. calculate checksum
        // 2. read footer
        // 3. compare checksum
        // step 1 must before step 2 as the writer goes
        long checksum = checkedInputStream.getChecksum().getValue();
        String s = Text.readStringWithChecksum(checkedInputStream);
        SRMetaBlockFooter footer = GsonUtils.GSON.fromJson(s, SRMetaBlockFooter.class);
        if (checksum != footer.getChecksum()) {
            throw new SRMetaBlockException(String.format(
                    "Invalid meta block, checksum mismatch! expect %d actual %d", footer.getChecksum(), checksum));
        }
    }
}