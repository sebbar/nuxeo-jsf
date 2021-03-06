/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id: URLPolicyServiceImpl.java 29556 2008-01-23 00:59:39Z jcarsique $
 */

package org.nuxeo.ecm.platform.ui.web.rest.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.ecm.platform.ui.web.auth.NXAuthConstants;
import org.nuxeo.ecm.platform.ui.web.auth.NuxeoAuthenticationFilter;
import org.nuxeo.ecm.platform.ui.web.rest.StaticNavigationHandler;
import org.nuxeo.ecm.platform.ui.web.rest.api.URLPolicyService;
import org.nuxeo.ecm.platform.ui.web.rest.descriptors.URLPatternDescriptor;
import org.nuxeo.ecm.platform.ui.web.rest.descriptors.ValueBindingDescriptor;
import org.nuxeo.ecm.platform.ui.web.util.BaseURL;
import org.nuxeo.ecm.platform.ui.web.util.ComponentTagUtils;
import org.nuxeo.ecm.platform.url.api.DocumentView;
import org.nuxeo.ecm.platform.url.api.DocumentViewCodecManager;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.runtime.api.Framework;

public class URLPolicyServiceImpl implements URLPolicyService {

    public static final String NAME = URLPolicyServiceImpl.class.getName();

    private static final Log log = LogFactory.getLog(URLPolicyServiceImpl.class);

    protected final Map<String, URLPatternDescriptor> descriptors;

    protected StaticNavigationHandler viewIdManager;

    public URLPolicyServiceImpl() {
        descriptors = new HashMap<String, URLPatternDescriptor>();
    }

    protected List<URLPatternDescriptor> getURLPatternDescriptors() {
        // TODO: add cache
        List<URLPatternDescriptor> lst = new ArrayList<URLPatternDescriptor>();
        for (URLPatternDescriptor desc : descriptors.values()) {
            if (desc.getEnabled()) {
                // add default at first
                if (desc.getDefaultURLPolicy()) {
                    lst.add(0, desc);
                } else {
                    lst.add(desc);
                }
            }
        }
        return lst;
    }

    protected URLPatternDescriptor getDefaultPatternDescriptor() {
        for (URLPatternDescriptor desc : descriptors.values()) {
            if (desc.getEnabled()) {
                if (desc.getDefaultURLPolicy()) {
                    return desc;
                }
            }
        }
        return null;
    }

    public String getDefaultPatternName() {
        URLPatternDescriptor desc = getDefaultPatternDescriptor();
        if (desc != null) {
            return desc.getName();
        }
        return null;
    }

    @Override
    public boolean hasPattern(String name) {
        URLPatternDescriptor desc = descriptors.get(name);
        return desc != null;
    }

    protected static DocumentViewCodecManager getDocumentViewCodecService() {
        try {
            return Framework.getService(DocumentViewCodecManager.class);
        } catch (Exception e) {
            log.error("Could not retrieve the document view service", e);
        }
        return null;
    }

    protected URLPatternDescriptor getURLPatternDescriptor(String patternName) {
        URLPatternDescriptor desc = descriptors.get(patternName);
        if (desc == null) {
            throw new IllegalArgumentException("Unknown pattern " + patternName);
        }
        return desc;
    }

    public boolean isCandidateForDecoding(HttpServletRequest httpRequest) {
        // only rewrite GET/HEAD URLs
        String method = httpRequest.getMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            return false;
        }

        // look for appropriate pattern and see if it needs filter
        // preprocessing
        URLPatternDescriptor desc = getURLPatternDescriptor(httpRequest);
        if (desc != null) {
            return desc.getNeedFilterPreprocessing();
        }
        // return default pattern descriptor behaviour
        URLPatternDescriptor defaultPattern = getDefaultPatternDescriptor();
        if (defaultPattern != null) {
            return defaultPattern.getNeedFilterPreprocessing();
        }
        return false;
    }

    public boolean isCandidateForEncoding(HttpServletRequest httpRequest) {
        Boolean forceEncoding = Boolean.FALSE;
        Object forceEncodingValue = httpRequest.getAttribute(FORCE_URL_ENCODING_REQUEST_KEY);
        if (forceEncodingValue instanceof Boolean) {
            forceEncoding = (Boolean) forceEncodingValue;
        }

        // only POST access need a redirect,unless with force encoding (this
        // happens when redirect is triggered after a seam page has been
        // processed)
        if (!forceEncoding.booleanValue()
                && !httpRequest.getMethod().equals("POST")) {
            return false;
        }

        Object skipRedirect = httpRequest.getAttribute(NXAuthConstants.DISABLE_REDIRECT_REQUEST_KEY);
        if (skipRedirect instanceof Boolean
                && ((Boolean) skipRedirect).booleanValue()) {
            return false;
        }

        // look for appropriate pattern and see if it needs redirect
        URLPatternDescriptor desc = getURLPatternDescriptor(httpRequest);
        if (desc != null) {
            return desc.getNeedRedirectFilter();
        }
        // return default pattern descriptor behaviour
        URLPatternDescriptor defaultPattern = getDefaultPatternDescriptor();
        if (defaultPattern != null) {
            return defaultPattern.getNeedRedirectFilter();
        }
        return false;
    }

    public void setDocumentViewInRequest(HttpServletRequest request,
            DocumentView docView) {
        request.setAttribute(NXAuthConstants.REQUESTED_URL,
                NuxeoAuthenticationFilter.getRequestedUrl(request));
        request.setAttribute(DOCUMENT_VIEW_REQUEST_KEY, docView);
    }

    protected URLPatternDescriptor getURLPatternDescriptor(
            HttpServletRequest request) {
        URLPatternDescriptor res = null;
        for (URLPatternDescriptor desc : getURLPatternDescriptors()) {
            DocumentView docView = getDocumentViewFromRequest(desc.getName(),
                    request);
            if (docView != null) {
                res = desc;
                break;
            }
        }
        // if (res == null && log.isDebugEnabled()) {
        // log.debug("Could not get url pattern for request "
        // + request.getRequestURL());
        // }
        return res;
    }

    public DocumentView getDocumentViewFromRequest(HttpServletRequest request) {
        DocumentView docView = null;
        for (URLPatternDescriptor desc : getURLPatternDescriptors()) {
            docView = getDocumentViewFromRequest(desc.getName(), request);
            if (docView != null) {
                break;
            }
        }

        // if (docView == null && log.isDebugEnabled()) {
        // log.debug("Could not get document view from request "
        // + request.getRequestURL());
        // }
        return docView;
    }

    public DocumentView getDocumentViewFromRequest(String patternName,
            HttpServletRequest request) {
        Object value = request.getAttribute(DOCUMENT_VIEW_REQUEST_KEY);
        if (value instanceof DocumentView) {
            DocumentView requestDocView = (DocumentView) value;
            // check if document view in request was set thanks to this pattern
            if (patternName.equals(requestDocView.getPatternName())) {
                return requestDocView;
            }
        }

        // try to build it from the request
        String url;
        String queryString = request.getQueryString();
        if (queryString != null) {
            url = new String(request.getRequestURL() + "?" + queryString);
        } else {
            url = new String(request.getRequestURL());
        }
        URLPatternDescriptor desc = getURLPatternDescriptor(patternName);
        String codecName = desc.getDocumentViewCodecName();
        DocumentViewCodecManager docViewService = getDocumentViewCodecService();
        DocumentView docView = docViewService.getDocumentViewFromUrl(codecName,
                url, desc.getNeedBaseURL(), BaseURL.getLocalBaseURL(request));
        if (docView != null) {
            // set pattern name
            docView.setPatternName(patternName);
            // set other parameters as set in the url pattern if docView does
            // not hold them already
            Map<String, String> docViewParameters = docView.getParameters();
            Map<String, String> requestParameters = URIUtils.getRequestParameters(queryString);
            if (requestParameters != null) {
                ValueBindingDescriptor[] bindings = desc.getValueBindings();
                for (ValueBindingDescriptor binding : bindings) {
                    String paramName = binding.getName();
                    if (!docViewParameters.containsKey(paramName)) {
                        Object paramValue = requestParameters.get(paramName);
                        if (paramValue == null || paramValue instanceof String) {
                            docView.addParameter(paramName, (String) paramValue);
                        }
                    }
                }
            }
        }

        return docView;
    }

    protected URLPatternDescriptor getURLPatternDescriptor(DocumentView docView) {
        URLPatternDescriptor res = null;
        if (docView != null) {
            String patternName = docView.getPatternName();
            try {
                res = getURLPatternDescriptor(patternName);
            } catch (IllegalArgumentException e) {
            }
        }
        // if (res == null && log.isDebugEnabled()) {
        // log.debug("Could not get url pattern for document view");
        // }
        return res;
    }

    public String getUrlFromDocumentView(DocumentView docView, String baseUrl) {
        String url = null;
        String patternName = docView.getPatternName();
        if (patternName != null) {
            // try with original document view pattern
            URLPatternDescriptor desc = getURLPatternDescriptor(patternName);
            if (desc != null) {
                // return corresponding url
                url = getUrlFromDocumentView(desc.getName(), docView, baseUrl);
            }
        }
        if (url == null) {
            // take first matching pattern
            List<URLPatternDescriptor> descs = getURLPatternDescriptors();
            for (URLPatternDescriptor desc : descs) {
                url = getUrlFromDocumentView(desc.getName(), docView, baseUrl);
                if (url != null) {
                    break;
                }
            }
        }
        // if (url == null && log.isDebugEnabled()) {
        // log.debug("Could not get url from document view");
        // }
        return url;
    }

    /**
     * Returns patterns sorted according to:
     * <ul>
     * <li>First patterns holding the given view id</li>
     * <li>The default pattern if it does not hold this view id</li>
     * <li>Other patterns not holding this view id</li>
     * </ul>
     *
     * @since 5.4.2
     * @param viewId
     * @deprecated since 5.5
     */
    @Deprecated
    protected List<URLPatternDescriptor> getSortedURLPatternDescriptorsFor(
            String viewId) {
        List<URLPatternDescriptor> sortedDescriptors = new ArrayList<URLPatternDescriptor>();
        List<URLPatternDescriptor> nonMatchingViewIdDescriptors = new ArrayList<URLPatternDescriptor>();

        List<URLPatternDescriptor> descriptors = getURLPatternDescriptors();
        for (URLPatternDescriptor descriptor : descriptors) {
            List<String> handledViewIds = descriptor.getViewIds();
            if (handledViewIds != null && handledViewIds.contains(viewId)) {
                sortedDescriptors.add(descriptor);
            } else {
                nonMatchingViewIdDescriptors.add(descriptor);
            }
        }
        sortedDescriptors.addAll(nonMatchingViewIdDescriptors);
        return sortedDescriptors;
    }

    public String getUrlFromDocumentView(String patternName,
            DocumentView docView, String baseUrl) {
        DocumentViewCodecManager docViewService = getDocumentViewCodecService();
        URLPatternDescriptor desc = getURLPatternDescriptor(patternName);
        String codecName = desc.getDocumentViewCodecName();
        return docViewService.getUrlFromDocumentView(codecName, docView,
                desc.getNeedBaseURL(), baseUrl);
    }

    public void applyRequestParameters(FacesContext facesContext) {
        // try to set document view
        ExpressionFactory ef = facesContext.getApplication().getExpressionFactory();
        ELContext context = facesContext.getELContext();

        HttpServletRequest httpRequest = (HttpServletRequest) facesContext.getExternalContext().getRequest();

        URLPatternDescriptor pattern = getURLPatternDescriptor(httpRequest);
        if (pattern == null) {
            return;
        }

        DocumentView docView = getDocumentViewFromRequest(pattern.getName(),
                httpRequest);
        // pattern applies => document view will not be null
        if (docView != null) {
            String documentViewBinding = pattern.getDocumentViewBinding();
            if (documentViewBinding != null && !"".equals(documentViewBinding)) {
                // try to set it from custom mapping
                ValueExpression ve = ef.createValueExpression(context,
                        pattern.getDocumentViewBinding(), Object.class);
                ve.setValue(context, docView);
            }
        }

        Map<String, String> docViewParameters = null;
        if (docView != null) {
            docViewParameters = docView.getParameters();
        }
        ValueBindingDescriptor[] bindings = pattern.getValueBindings();
        if (bindings != null) {
            for (ValueBindingDescriptor binding : bindings) {
                if (!binding.getCallSetter()) {
                    continue;
                }
                String paramName = binding.getName();
                // try doc view parameters
                Object value = null;
                if (docViewParameters != null
                        && docViewParameters.containsKey(paramName)) {
                    value = docView.getParameter(paramName);
                } else {
                    // try request attributes
                    value = httpRequest.getAttribute(paramName);
                }
                String expr = binding.getExpression();
                if (ComponentTagUtils.isValueReference(expr)) {
                    ValueExpression ve = ef.createValueExpression(context,
                            expr, Object.class);
                    try {
                        ve.setValue(context, value);
                    } catch (Exception e) {
                        log.error(String.format(
                                "Could not apply request parameter %s "
                                        + "to expression %s", value, expr), e);
                    }
                }
            }
        }
    }

    public void appendParametersToRequest(FacesContext facesContext) {
        appendParametersToRequest(facesContext, null);
    }

    public void appendParametersToRequest(FacesContext facesContext,
            String pattern) {
        // try to get doc view from custom mapping
        DocumentView docView = null;
        ExpressionFactory ef = facesContext.getApplication().getExpressionFactory();
        ELContext context = facesContext.getELContext();
        HttpServletRequest httpRequest = (HttpServletRequest) facesContext.getExternalContext().getRequest();

        // get existing document view from given pattern, else create it
        URLPatternDescriptor patternDesc = null;
        if (pattern != null && !"".equals(pattern)) {
            patternDesc = getURLPatternDescriptor(pattern);
        } else {
            // iterate over pattern descriptors, and take the first one that
            // applies, or use the default one
            List<URLPatternDescriptor> descs = getURLPatternDescriptors();
            boolean applies = false;
            for (URLPatternDescriptor desc : descs) {
                String documentViewAppliesExpr = desc.getDocumentViewBindingApplies();
                if (!StringUtils.isBlank(documentViewAppliesExpr)) {
                    // TODO: maybe put view id to the request to help writing
                    // the EL expression
                    ValueExpression ve = ef.createValueExpression(context,
                            documentViewAppliesExpr, Object.class);
                    try {
                        Object res = ve.getValue(context);
                        if (Boolean.TRUE.equals(res)) {
                            applies = true;
                        }
                    } catch (Exception e) {
                        if (log.isDebugEnabled()) {
                            log.debug(String.format(
                                    "Error executing expression '%s' for "
                                            + "url pattern '%s': %s",
                                    documentViewAppliesExpr, desc.getName(),
                                    e.getMessage()));
                        }
                    }
                }
                if (applies) {
                    patternDesc = desc;
                    break;
                }
            }
            if (patternDesc == null) {
                // default on the default pattern desc
                patternDesc = getDefaultPatternDescriptor();
            }
        }
        if (patternDesc != null) {
            // resolved doc view values thanks to bindings
            Object docViewValue = null;
            String documentViewBinding = patternDesc.getDocumentViewBinding();
            if (!StringUtils.isBlank(documentViewBinding)) {
                ValueExpression ve = ef.createValueExpression(context,
                        documentViewBinding, Object.class);
                docViewValue = ve.getValue(context);
            }
            if (docViewValue == null) {
                documentViewBinding = patternDesc.getNewDocumentViewBinding();
                if (!StringUtils.isBlank(documentViewBinding)) {
                    ValueExpression ve = ef.createValueExpression(context,
                            documentViewBinding, Object.class);
                    docViewValue = ve.getValue(context);
                }
            }
            if (docViewValue instanceof DocumentView) {
                docView = (DocumentView) docViewValue;
                // set pattern name in case it was just created
                docView.setPatternName(patternDesc.getName());
                ValueBindingDescriptor[] bindings = patternDesc.getValueBindings();
                if (bindings != null) {
                    for (ValueBindingDescriptor binding : bindings) {
                        if (!binding.getCallGetter()) {
                            continue;
                        }
                        String paramName = binding.getName();
                        String expr = binding.getExpression();
                        try {
                            Object value;
                            if (ComponentTagUtils.isValueReference(expr)) {
                                ValueExpression ve = ef.createValueExpression(
                                        context, expr, Object.class);
                                value = ve.getValue(context);
                            } else {
                                value = expr;
                            }
                            if (docView != null) {
                                // do not set attributes on the request as
                                // document view will be put in the request
                                // anyway
                                docView.addParameter(paramName, (String) value);
                            } else {
                                httpRequest.setAttribute(paramName, value);
                            }
                        } catch (Exception e) {
                            log.error(
                                    String.format(
                                            "Could not get parameter %s from expression %s",
                                            paramName, expr), e);
                        }
                    }
                }
            }
        }

        // save document view to the request
        setDocumentViewInRequest(httpRequest, docView);
    }

    public String navigate(FacesContext facesContext) {
        HttpServletRequest httpRequest = (HttpServletRequest) facesContext.getExternalContext().getRequest();

        URLPatternDescriptor pattern = getURLPatternDescriptor(httpRequest);
        if (pattern == null) {
            return null;
        }

        DocumentView docView = getDocumentViewFromRequest(pattern.getName(),
                httpRequest);
        ExpressionFactory ef = facesContext.getApplication().getExpressionFactory();
        ELContext context = facesContext.getELContext();
        String actionBinding = pattern.getActionBinding();
        if (actionBinding != null && !"".equals(actionBinding)) {
            MethodExpression action = ef.createMethodExpression(context,
                    actionBinding, String.class,
                    new Class[] { DocumentView.class });
            return (String) action.invoke(context, new Object[] { docView });
        }
        return null;
    }

    // registries management

    public void addPatternDescriptor(URLPatternDescriptor pattern) {
        String name = pattern.getName();
        if (descriptors.containsKey(name)) {
            // no merging right now
            descriptors.remove(name);
        }
        descriptors.put(pattern.getName(), pattern);
        log.debug("Added URLPatternDescriptor: " + name);
    }

    public void removePatternDescriptor(URLPatternDescriptor pattern) {
        String name = pattern.getName();
        descriptors.remove(name);
        log.debug("Removed URLPatternDescriptor: " + name);
    }

    @Override
    public void initViewIdManager(ServletContext context) {
        if (viewIdManager == null) {
            viewIdManager = new StaticNavigationHandler(context);
        }
    }

    StaticNavigationHandler getViewIdManager() {
        if (viewIdManager == null) {
            throw new RuntimeException("View id manager is not initialized: "
                    + "URLPolicyService#initViewIdManager should "
                    + "have been called first");
        }
        return viewIdManager;
    }

    @Override
    public String getOutcomeFromViewId(String viewId,
            HttpServletRequest httpRequest) {
        return getViewIdManager().getOutcomeFromViewId(viewId);
    }

    @Override
    public String getOutcomeFromUrl(String url, HttpServletRequest request) {
        String baseUrl = BaseURL.getBaseURL(request);
        // parse url to get outcome from view id
        String viewId = url;
        String webAppName = "/" + VirtualHostHelper.getWebAppName(request);
        if (viewId.startsWith(baseUrl)) {
            // url is absolute
            viewId = '/' + viewId.substring(baseUrl.length());
        } else if (viewId.startsWith(webAppName)) {
            // url is relative to the web app
            viewId = viewId.substring(webAppName.length());
        }
        int index = viewId.indexOf('?');
        if (index != -1) {
            viewId = viewId.substring(0, index);
        }
        return getOutcomeFromViewId(viewId, request);
    }

    @Override
    public String getViewIdFromOutcome(String outcome,
            HttpServletRequest httpRequest) {
        return getViewIdManager().getViewIdFromOutcome(outcome);
    }

    public void clear() {
        descriptors.clear();
    }

    @Override
    public void flushCache() {
        viewIdManager = null;
    }

}
