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
package org.apache.chemistry.opencmis.client.bindings.impl;

import java.util.Map;

import org.apache.chemistry.opencmis.client.bindings.spi.AbstractAuthenticationProvider;
import org.apache.chemistry.opencmis.client.bindings.spi.CmisSpi;
import org.apache.chemistry.opencmis.client.bindings.spi.Session;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.BindingsObjectFactoryImpl;
import org.apache.chemistry.opencmis.commons.spi.AclService;
import org.apache.chemistry.opencmis.commons.spi.BindingsObjectFactory;
import org.apache.chemistry.opencmis.commons.spi.CmisBinding;
import org.apache.chemistry.opencmis.commons.spi.DiscoveryService;
import org.apache.chemistry.opencmis.commons.spi.MultiFilingService;
import org.apache.chemistry.opencmis.commons.spi.NavigationService;
import org.apache.chemistry.opencmis.commons.spi.ObjectService;
import org.apache.chemistry.opencmis.commons.spi.PolicyService;
import org.apache.chemistry.opencmis.commons.spi.RelationshipService;
import org.apache.chemistry.opencmis.commons.spi.RepositoryService;
import org.apache.chemistry.opencmis.commons.spi.VersioningService;

/**
 * CMIS binding implementation.
 */
public class CmisBindingImpl implements CmisBinding {

    private static final long serialVersionUID = 1L;

    private Session session;
    private BindingsObjectFactory objectFactory;
    private RepositoryService repositoryServiceWrapper;

    /**
     * Constructor.
     * 
     * @param sessionParameters
     *            the session parameters
     */
    public CmisBindingImpl(Map<String, String> sessionParameters) {
        // some checks first
        if (sessionParameters == null) {
            throw new IllegalArgumentException("Session parameters must be set!");
        }
        if (!sessionParameters.containsKey(SessionParameter.BINDING_SPI_CLASS)) {
            throw new IllegalArgumentException("Session parameters do not contain a SPI class name!");
        }

        // initialize session
        session = new SessionImpl();
        for (Map.Entry<String, String> entry : sessionParameters.entrySet()) {
            session.put(entry.getKey(), entry.getValue());
        }

        // create authentication provider and add it session
        String authProvider = sessionParameters.get(SessionParameter.AUTHENTICATION_PROVIDER_CLASS);
        if (authProvider != null) {
            Object authProviderObj = null;

            try {
                authProviderObj = Class.forName(authProvider).newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not load authentication provider: " + e, e);
            }

            if (!(authProviderObj instanceof AbstractAuthenticationProvider)) {
                throw new IllegalArgumentException(
                        "Authentication provider does not extend AbstractAuthenticationProvider!");
            }

            session.put(CmisBindingsHelper.AUTHENTICATION_PROVIDER_OBJECT,
                    (AbstractAuthenticationProvider) authProviderObj);
            ((AbstractAuthenticationProvider) authProviderObj).setSession(session);
        }

        // set up caches
        clearAllCaches();

        // initialize the SPI
        CmisBindingsHelper.getSPI(session);

        // set up object factory
        objectFactory = new BindingsObjectFactoryImpl();

        // set up repository service
        repositoryServiceWrapper = new RepositoryServiceImpl(session);
    }

    public RepositoryService getRepositoryService() {
        checkSession();
        return repositoryServiceWrapper;
    }

    public NavigationService getNavigationService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getNavigationService();
    }

    public ObjectService getObjectService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getObjectService();
    }

    public DiscoveryService getDiscoveryService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getDiscoveryService();
    }

    public RelationshipService getRelationshipService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getRelationshipService();
    }

    public VersioningService getVersioningService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getVersioningService();
    }

    public AclService getAclService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getAclService();
    }

    public MultiFilingService getMultiFilingService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getMultiFilingService();
    }

    public PolicyService getPolicyService() {
        checkSession();
        CmisSpi spi = CmisBindingsHelper.getSPI(session);
        return spi.getPolicyService();
    }

    public BindingsObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public void clearAllCaches() {
        checkSession();

        session.writeLock();
        try {
            session.put(CmisBindingsHelper.REPOSITORY_INFO_CACHE, new RepositoryInfoCache(session));
            session.put(CmisBindingsHelper.TYPE_DEFINTION_CACHE, new TypeDefinitionCache(session));

            CmisSpi spi = CmisBindingsHelper.getSPI(session);
            spi.clearAllCaches();
        } finally {
            session.writeUnlock();
        }
    }

    public void clearRepositoryCache(String repositoryId) {
        checkSession();

        if (repositoryId == null) {
            return;
        }

        session.writeLock();
        try {
            RepositoryInfoCache repInfoCache = (RepositoryInfoCache) session
                    .get(CmisBindingsHelper.REPOSITORY_INFO_CACHE);
            repInfoCache.remove(repositoryId);

            TypeDefinitionCache typeDefCache = (TypeDefinitionCache) session
                    .get(CmisBindingsHelper.TYPE_DEFINTION_CACHE);
            typeDefCache.remove(repositoryId);

            CmisSpi spi = CmisBindingsHelper.getSPI(session);
            spi.clearRepositoryCache(repositoryId);
        } finally {
            session.writeUnlock();
        }
    }

    public void close() {
        checkSession();

        session.writeLock();
        try {
            CmisSpi spi = CmisBindingsHelper.getSPI(session);
            spi.close();
        } finally {
            session.writeUnlock();
            session = null;
        }

    }

    private void checkSession() {
        if (session == null) {
            throw new IllegalStateException("Already closed.");
        }
    }
}