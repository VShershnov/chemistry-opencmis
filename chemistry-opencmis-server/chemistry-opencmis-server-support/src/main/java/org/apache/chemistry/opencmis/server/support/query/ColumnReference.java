/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.server.support.query;

import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;

public class ColumnReference extends CmisSelector {

    private String typeQueryName;  // type query name coming from query statement
    private String propQueryName;  // property query name coming from query statement

    // The following fields are set when the types are resolved:
    private String propertyId;
    private TypeDefinition typeDef;

    public ColumnReference(String typeQueryName, String propQueryName) {
        this.typeQueryName = typeQueryName;
        this.propQueryName = propQueryName;
    }

    public ColumnReference(String propQueryName) {
        this.typeQueryName = null;
        this.propQueryName = propQueryName;
    }

    public String getTypeQueryName() {
        return typeQueryName;
    }

    public String getPropertyQueryName() {
        return propQueryName;
    }

    @Override
    public String getName() {
        return propQueryName;
    }

    public void setTypeDefinition(String propertyId, TypeDefinition typeDef) {
        this.typeDef = typeDef;
        this.propertyId = propertyId;
    }

    public TypeDefinition getTypeDefinition() {
        return typeDef;
    }

    public String getPropertyId() {
        return propertyId;
    }

    @Override
    public String toString() {
        return "Column Reference for column " + propQueryName + " in type " + typeQueryName;
    }
}
