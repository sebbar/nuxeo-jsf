/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.webapp.contentbrowser;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.web.RequestParameter;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;

/**
 * Provides suggestion methods on documents
 *
 * @since 5.4.2
 */
@Name("docSuggestionActions")
@Scope(ScopeType.PAGE)
public class DocumentSuggestionActions implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_PAGE_PROVIDER_NAME = "default_document_suggestion";

    @In(required = true)
    protected transient CoreSession documentManager;

    @RequestParameter
    protected String pageProviderName;

    protected String cachedPageProviderName;

    protected Object cachedInput;

    protected List<DocumentModel> cachedSuggestions;

    public List<DocumentModel> getDocSuggestions(Object input)
            throws ClientException {
        if (equals(cachedPageProviderName, pageProviderName)
                && equals(cachedInput, input)) {
            return cachedSuggestions;
        }

        String pattern = (String) input;
        PageProvider<DocumentModel> pp = getPageProvider(pattern);
        List<DocumentModel> documents = pp.getCurrentPage();
        cachedInput = input;
        cachedSuggestions = documents;
        return documents;
    }

    @SuppressWarnings("unchecked")
    protected PageProvider<DocumentModel> getPageProvider(String pattern)
            throws ClientException {
        String ppName = DEFAULT_PAGE_PROVIDER_NAME;
        if (pageProviderName != null && !StringUtils.isEmpty(pageProviderName)) {
            ppName = pageProviderName;
        }
        try {
            PageProviderService ppService = Framework.getService(PageProviderService.class);
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                    (Serializable) documentManager);
            PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) ppService.getPageProvider(
                    ppName, null, null, null, props, new Object[] { pattern });
            if (pp == null) {
                throw new ClientException("Page provider not found: " + ppName);
            }
            return pp;
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("Error searching for documents", e);
        }
    }

    public boolean getDocumentExistsAndIsVisibleWithId(String id)
            throws ClientException {
        if (StringUtils.isEmpty(id)) {
            return false;
        }
        IdRef ref = new IdRef(id);
        return documentManager.exists(ref)
                && documentManager.hasPermission(ref, SecurityConstants.READ);
    }

    public boolean getDocumentExistsAndIsVisibleWithPath(String path)
            throws ClientException {
        if (StringUtils.isEmpty(path)) {
            return false;
        }
        PathRef ref = new PathRef(path);
        return documentManager.exists(ref)
                && documentManager.hasPermission(ref, SecurityConstants.READ);
    }

    public DocumentModel getDocumentWithId(String id) throws ClientException {
        if (StringUtils.isEmpty(id)) {
            return null;
        }
        return documentManager.getDocument(new IdRef(id));
    }

    public DocumentModel getDocumentWithPath(String path)
            throws ClientException {
        if (StringUtils.isEmpty(path)) {
            return null;
        }
        return documentManager.getDocument(new PathRef(path));
    }

    protected boolean equals(Object item1, Object item2) {
        if (item1 == null && item2 == null) {
            return true;
        } else if (item1 == null) {
            return false;
        } else {
            return item1.equals(item2);
        }
    }

}
