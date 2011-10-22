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
package org.apache.chemistry.opencmis.server.impl.browser;

import static org.apache.chemistry.opencmis.commons.impl.Constants.PARAM_OBJECT_ID;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CMISACTION_CREATE_DOCUMENT;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CMISACTION_CREATE_FOLDER;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CMISACTION_DELETE;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CMISACTION_DELETE_TREE;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CMISACTION_QUERY;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CMISACTION_SET_CONTENT;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CONTEXT_BASETYPE_ID;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CONTEXT_TRANSACTION;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CONTROL_CMISACTION;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CONTROL_OBJECT_ID;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CONTROL_TRANSACTION;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.JSON_MIME_TYPE;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.PARAM_SELECTOR;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_CHILDREN;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_CONTENT;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_DESCENDANTS;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_FOLDER_TREE;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_LAST_RESULT;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_OBJECT;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_PARENTS;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_QUERY;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_TYPE_CHILDREN;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_TYPE_DEFINITION;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_TYPE_DESCENDANTS;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.SELECTOR_VERSIONS;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.createCookieValue;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.prepareContext;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.setCookie;
import static org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.writeJSON;
import static org.apache.chemistry.opencmis.server.impl.browser.json.JSONConstants.ERROR_EXCEPTION;
import static org.apache.chemistry.opencmis.server.impl.browser.json.JSONConstants.ERROR_MESSAGE;
import static org.apache.chemistry.opencmis.server.impl.browser.json.JSONConstants.ERROR_STACKTRACE;
import static org.apache.chemistry.opencmis.server.shared.Dispatcher.METHOD_GET;
import static org.apache.chemistry.opencmis.server.shared.Dispatcher.METHOD_POST;
import static org.apache.chemistry.opencmis.server.shared.HttpUtils.getStringParameter;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConstraintException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisContentAlreadyExistsException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisFilterNotValidException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNameConstraintViolationException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNotSupportedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStorageException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStreamNotSupportedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUpdateConflictException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisVersioningException;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.server.CmisService;
import org.apache.chemistry.opencmis.commons.server.CmisServiceFactory;
import org.apache.chemistry.opencmis.server.impl.CmisRepositoryContextListener;
import org.apache.chemistry.opencmis.server.impl.ServerVersion;
import org.apache.chemistry.opencmis.server.impl.browser.BrowserBindingUtils.CallUrl;
import org.apache.chemistry.opencmis.server.shared.CallContextHandler;
import org.apache.chemistry.opencmis.server.shared.Dispatcher;
import org.apache.chemistry.opencmis.server.shared.ExceptionHelper;
import org.apache.chemistry.opencmis.server.shared.HttpUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;

public class CmisBrowserBindingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String PARAM_CALL_CONTEXT_HANDLER = "callContextHandler";

    private static final Log LOG = LogFactory.getLog(CmisBrowserBindingServlet.class.getName());

    private Dispatcher repositoryDispatcher;
    private Dispatcher rootDispatcher;
    private CallContextHandler callContextHandler;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // initialize the call context handler
        callContextHandler = null;
        String callContextHandlerClass = config.getInitParameter(PARAM_CALL_CONTEXT_HANDLER);
        if (callContextHandlerClass != null) {
            try {
                callContextHandler = (CallContextHandler) Class.forName(callContextHandlerClass).newInstance();
            } catch (Exception e) {
                throw new ServletException("Could not load call context handler: " + e, e);
            }
        }

        // initialize the dispatchers
        repositoryDispatcher = new Dispatcher();
        rootDispatcher = new Dispatcher();

        try {
            repositoryDispatcher.addResource("", METHOD_GET, RepositoryService.class, "getRepositoryInfo");
            repositoryDispatcher
                    .addResource(SELECTOR_LAST_RESULT, METHOD_GET, RepositoryService.class, "getLastResult");
            repositoryDispatcher.addResource(SELECTOR_TYPE_CHILDREN, METHOD_GET, RepositoryService.class,
                    "getTypeChildren");
            repositoryDispatcher.addResource(SELECTOR_TYPE_DESCENDANTS, METHOD_GET, RepositoryService.class,
                    "getTypeDescendants");
            repositoryDispatcher.addResource(SELECTOR_TYPE_DEFINITION, METHOD_GET, RepositoryService.class,
                    "getTypeDefinition");
            repositoryDispatcher.addResource(SELECTOR_QUERY, METHOD_GET, DiscoveryService.class, "query");
            repositoryDispatcher.addResource(CMISACTION_QUERY, METHOD_POST, DiscoveryService.class, "query");
            repositoryDispatcher.addResource(CMISACTION_CREATE_DOCUMENT, METHOD_POST, ObjectService.class,
                    "createDocument");

            rootDispatcher.addResource(SELECTOR_OBJECT, METHOD_GET, ObjectService.class, "getObject");
            rootDispatcher.addResource(SELECTOR_CONTENT, METHOD_GET, ObjectService.class, "getContentStream");
            rootDispatcher.addResource(SELECTOR_CHILDREN, METHOD_GET, NavigationService.class, "getChildren");
            rootDispatcher.addResource(SELECTOR_DESCENDANTS, METHOD_GET, NavigationService.class, "getDescendants");
            rootDispatcher.addResource(SELECTOR_FOLDER_TREE, METHOD_GET, NavigationService.class, "getFolderTree");
            rootDispatcher.addResource(SELECTOR_PARENTS, METHOD_GET, NavigationService.class, "getObjectParents");
            rootDispatcher.addResource(SELECTOR_VERSIONS, METHOD_GET, VersioningService.class, "getAllVersions");

            rootDispatcher.addResource(CMISACTION_CREATE_DOCUMENT, METHOD_POST, ObjectService.class, "createDocument");
            rootDispatcher.addResource(CMISACTION_CREATE_FOLDER, METHOD_POST, ObjectService.class, "createFolder");
            rootDispatcher.addResource(CMISACTION_SET_CONTENT, METHOD_POST, ObjectService.class, "setContentStream");
            rootDispatcher.addResource(CMISACTION_DELETE, METHOD_POST, ObjectService.class, "deleteObject");
            rootDispatcher.addResource(CMISACTION_DELETE_TREE, METHOD_POST, ObjectService.class, "deleteTree");
        } catch (NoSuchMethodException e) {
            LOG.error("Cannot initialize dispatcher!", e);
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException,
            IOException {

        // set default headers
        response.addHeader("Cache-Control", "private, max-age=0");
        response.addHeader("Server", ServerVersion.OPENCMIS_SERVER);

        // create a context object, dispatch and handle exceptions
        CallContext context = null;
        try {
            context = HttpUtils.createContext(request, response, getServletContext(), CallContext.BINDING_BROWSER,
                    callContextHandler);
            dispatch(context, request, response);
        } catch (Exception e) {
            if (e instanceof CmisPermissionDeniedException) {
                if (context == null || context.getUsername() == null) {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"CMIS\"");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorization Required");
                } else {
                    printError(e, request, response, context);
                }
            } else {
                printError(e, request, response, context);
            }
        }

        // we are done.
        response.flushBuffer();
    }

    // --------------------------------------------------------

    private void dispatch(CallContext context, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        CmisService service = null;
        try {
            // get services factory
            CmisServiceFactory factory = (CmisServiceFactory) getServletContext().getAttribute(
                    CmisRepositoryContextListener.SERVICES_FACTORY);

            if (factory == null) {
                throw new CmisRuntimeException("Service factory not available! Configuration problem?");
            }

            // get the service
            service = factory.getService(context);

            // analyze the path
            String[] pathFragments = HttpUtils.splitPath(request);

            if (pathFragments.length < 1) {
                // root -> repository infos
                RepositoryService.getRepositories(context, service, request, response);
                return;
            }

            // select dispatcher

            CallUrl callUrl = null;
            if (pathFragments.length == 1) {
                callUrl = CallUrl.REPOSITORY;
            } else if (BrowserBindingUtils.ROOT_PATH_FRAGMENT.equals(pathFragments[1])) {
                callUrl = CallUrl.ROOT;
            }

            if (callUrl == null) {
                throw new CmisNotSupportedException("Unknown operation");
            }

            String method = request.getMethod();
            String repositoryId = pathFragments[0];
            boolean methodFound = false;

            if (METHOD_GET.equals(method)) {
                String selector = getStringParameter(request, PARAM_SELECTOR);
                String objectId = getStringParameter(request, PARAM_OBJECT_ID);

                // add object id and object base type id to context
                prepareContext(context, callUrl, service, repositoryId, objectId, null, request);

                // dispatch
                if (callUrl == CallUrl.REPOSITORY) {
                    if (selector == null) {
                        selector = "";
                    }

                    methodFound = repositoryDispatcher.dispatch(selector, method, context, service, repositoryId,
                            request, response);
                } else if (callUrl == CallUrl.ROOT) {
                    // set default method if necessary
                    if (selector == null) {
                        try {
                            BaseTypeId basetype = BaseTypeId.fromValue((String) context.get(CONTEXT_BASETYPE_ID));
                            switch (basetype) {
                            case CMIS_DOCUMENT:
                                selector = SELECTOR_CONTENT;
                                break;
                            case CMIS_FOLDER:
                                selector = SELECTOR_CHILDREN;
                                break;
                            default:
                                selector = SELECTOR_OBJECT;
                                break;
                            }
                        } catch (Exception e) {
                            selector = SELECTOR_OBJECT;
                        }
                    }

                    methodFound = rootDispatcher.dispatch(selector, method, context, service, repositoryId, request,
                            response);
                }
            } else if (METHOD_POST.equals(method)) {
                POSTHttpServletRequestWrapper postRequest = new POSTHttpServletRequestWrapper(request);

                String cmisaction = getStringParameter(postRequest, CONTROL_CMISACTION);
                String objectId = getStringParameter(postRequest, CONTROL_OBJECT_ID);
                String transaction = getStringParameter(postRequest, CONTROL_TRANSACTION);

                if (cmisaction == null || cmisaction.length() == 0) {
                    throw new CmisNotSupportedException("Unknown action");
                }

                // add object id and object base type id to context
                prepareContext(context, callUrl, service, repositoryId, objectId, transaction, postRequest);

                // dispatch
                if (callUrl == CallUrl.REPOSITORY) {
                    methodFound = repositoryDispatcher.dispatch(cmisaction, method, context, service, repositoryId,
                            postRequest, response);
                } else if (callUrl == CallUrl.ROOT) {
                    methodFound = rootDispatcher.dispatch(cmisaction, method, context, service, repositoryId,
                            postRequest, response);
                }
            }

            // if the dispatcher couldn't find a matching method, return an
            // error message
            if (!methodFound) {
                throw new CmisNotSupportedException("Unknown operation");
            }
        } finally {
            if (service != null) {
                service.close();
            }
        }
    }

    /**
     * Translates an exception in an appropriate HTTP error code.
     */
    private static int getErrorCode(CmisBaseException ex) {
        if (ex instanceof CmisConstraintException) {
            return 409;
        } else if (ex instanceof CmisContentAlreadyExistsException) {
            return 409;
        } else if (ex instanceof CmisFilterNotValidException) {
            return 400;
        } else if (ex instanceof CmisInvalidArgumentException) {
            return 400;
        } else if (ex instanceof CmisNameConstraintViolationException) {
            return 409;
        } else if (ex instanceof CmisNotSupportedException) {
            return 405;
        } else if (ex instanceof CmisObjectNotFoundException) {
            return 404;
        } else if (ex instanceof CmisPermissionDeniedException) {
            return 403;
        } else if (ex instanceof CmisStorageException) {
            return 500;
        } else if (ex instanceof CmisStreamNotSupportedException) {
            return 403;
        } else if (ex instanceof CmisUpdateConflictException) {
            return 409;
        } else if (ex instanceof CmisVersioningException) {
            return 409;
        }

        return 500;
    }

    /**
     * Prints the error as JSON.
     */
    @SuppressWarnings("unchecked")
    private static void printError(Exception ex, HttpServletRequest request, HttpServletResponse response,
            CallContext context) {
        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String exceptionName = "runtime";

        if (ex instanceof CmisRuntimeException) {
            LOG.error(ex.getMessage(), ex);
        } else if (ex instanceof CmisBaseException) {
            statusCode = getErrorCode((CmisBaseException) ex);
            exceptionName = ((CmisBaseException) ex).getExceptionName();
        } else {
            LOG.error(ex.getMessage(), ex);
        }

        response.setStatus(statusCode);
        response.setContentType(JSON_MIME_TYPE);
        if (context != null) {
            setCookie(request, response, context.getRepositoryId(), (String) context.get(CONTEXT_TRANSACTION),
                    createCookieValue(statusCode, null, exceptionName, ex.getMessage()));
        }

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put(ERROR_EXCEPTION, exceptionName);
        jsonResponse.put(ERROR_MESSAGE, ex.getMessage());

        String st = ExceptionHelper.getStacktraceAsString(ex);
        if (st != null) {
            jsonResponse.put(ERROR_STACKTRACE, st);
        }

        try {
            writeJSON(jsonResponse, request, response);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}