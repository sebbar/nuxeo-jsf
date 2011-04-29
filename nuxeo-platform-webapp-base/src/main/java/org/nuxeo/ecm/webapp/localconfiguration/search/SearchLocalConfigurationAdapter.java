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
 *     eugen
 */
package org.nuxeo.ecm.webapp.localconfiguration.search;

import static org.nuxeo.ecm.webapp.localconfiguration.search.SearchLocalConfigurationConstants.DEFAULT_ADVANCED_SEARCH_VIEW;
import static org.nuxeo.ecm.webapp.localconfiguration.search.SearchLocalConfigurationConstants.FIELD_ADVANCED_SEARCH_VIEW;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * @author <a href="mailto:ei@nuxeo.com">Eugen Ionica</a>
 *
 */
public class SearchLocalConfigurationAdapter implements
        SearchLocalConfiguration {

    private static final Log log = LogFactory.getLog(SearchLocalConfigurationAdapter.class);

    DocumentModel doc;

    public SearchLocalConfigurationAdapter(DocumentModel doc) {
        super();
        this.doc = doc;
    }

    @Override
    public DocumentRef getDocumentRef() {
        return doc.getRef();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.nuxeo.ecm.core.api.localconfiguration.LocalConfiguration#canMerge()
     */
    @Override
    public boolean canMerge() {
        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.nuxeo.ecm.core.api.localconfiguration.LocalConfiguration#merge(java
     * .lang.Object)
     */
    @Override
    public SearchLocalConfiguration merge(SearchLocalConfiguration other) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.nuxeo.ecm.webapp.localconfiguration.AdvancedSearchConfiguration#
     * getAdvancedSearchView()
     */
    @Override
    public String getAdvancedSearchView() {
        String value = DEFAULT_ADVANCED_SEARCH_VIEW;
        try {
            value = (String) doc.getPropertyValue(FIELD_ADVANCED_SEARCH_VIEW);
        } catch (ClientException e) {
            log.debug("cann't get default type for:" + doc.getPathAsString(), e);
        }
        return value;
    }

}
