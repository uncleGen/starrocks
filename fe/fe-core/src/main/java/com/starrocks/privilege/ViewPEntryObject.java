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


package com.starrocks.privilege;

import com.starrocks.catalog.Database;
import com.starrocks.catalog.Table;
import com.starrocks.server.GlobalStateMgr;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * View is a subclass of table, only the table type is different
 */
public class ViewPEntryObject extends TablePEntryObject {
    protected ViewPEntryObject(long databaseId, long tableId) {
        super(databaseId, tableId);
    }

    public static ViewPEntryObject generate(GlobalStateMgr mgr, List<String> tokens) throws PrivilegeException {
        if (tokens.size() != 2) {
            throw new PrivilegeException("invalid object tokens, should have two: " + tokens);
        }
        Database database = mgr.getDb(tokens.get(0));
        if (database == null) {
            throw new PrivilegeException("cannot find db: " + tokens.get(0));
        }
        Table table = database.getTable(tokens.get(1));
        if (table == null || !table.getType().equals(Table.TableType.VIEW)) {
            throw new PrivilegeException("cannot find view " + tokens.get(1) + " in db " + tokens.get(0));
        }
        return new ViewPEntryObject(database.getId(), table.getId());
    }

    public static ViewPEntryObject generate(
            GlobalStateMgr mgr, List<String> allTypes, String restrictType, String restrictName)
            throws PrivilegeException {
        if (allTypes.size() == 1) {
            if (StringUtils.isEmpty(restrictType)
                    || !restrictType.equals(PrivilegeType.DATABASE.toString())
                    || StringUtils.isEmpty(restrictName)) {
                throw new PrivilegeException("ALL VIEWS must be restricted with database!");
            }

            Database database = mgr.getDb(restrictName);
            if (database == null) {
                throw new PrivilegeException("cannot find db: " + restrictName);
            }
            return new ViewPEntryObject(database.getId(), ALL_TABLES_ID);
        } else if (allTypes.size() == 2) {
            if (!allTypes.get(1).equals(PrivilegeType.DATABASE.getPlural())) {
                throw new PrivilegeException(
                        "ALL VIEWS must be restricted with ALL DATABASES instead of ALL " + allTypes.get(1));
            }
            return new ViewPEntryObject(ALL_DATABASE_ID, ALL_TABLES_ID);
        } else {
            throw new PrivilegeException("invalid ALL statement for views!");
        }
    }
}
