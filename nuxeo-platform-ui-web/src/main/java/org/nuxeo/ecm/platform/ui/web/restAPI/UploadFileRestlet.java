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
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.platform.ui.web.restAPI;

import static org.jboss.seam.ScopeType.EVENT;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.tag.fn.LiveEditConstants;
import org.nuxeo.ecm.platform.util.RepositoryLocation;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * Restlet to help LiveEdit clients update the blob content of a document
 *
 * @author Sun Tan <stan@nuxeo.com>
 * @author Olivier Grisel <ogrisel@nuxeo.com>
 */
@Name("uploadFileRestlet")
@Scope(EVENT)
public class UploadFileRestlet extends BaseNuxeoRestlet implements
        LiveEditConstants, Serializable {

    private static final long serialVersionUID = -6167207806181917456L;

    @In(create = true)
    protected transient NavigationContext navigationContext;

    protected CoreSession documentManager;

    @SuppressWarnings("deprecation")
    @Override
    public void handle(Request req, Response res) {
        String repo = (String) req.getAttributes().get("repo");
        String docid = (String) req.getAttributes().get("docid");
        String filename = (String) req.getAttributes().get("filename");
        try {
            filename = URLDecoder.decode(filename, URL_ENCODE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            handleError(res, e);
            return;
        }

        if (repo == null || repo.equals("*")) {
            handleError(res, "you must specify a repository");
            return;
        }

        DocumentModel dm = null;
        try {
            navigationContext.setCurrentServerLocation(new RepositoryLocation(
                    repo));
            documentManager = navigationContext.getOrCreateDocumentManager();
            if (docid != null) {
                dm = documentManager.getDocument(new IdRef(docid));
            }
        } catch (ClientException e) {
            handleError(res, e);
            return;
        }

        try {

            String blobPropertyName = getQueryParamValue(req,
                    BLOB_PROPERTY_NAME, null);
            String filenamePropertyName = getQueryParamValue(req,
                    FILENAME_PROPERTY_NAME, null);

            if (blobPropertyName == null || filenamePropertyName == null) {
                // find the names of the fields from the optional request
                // parameters with fallback to defaults if none is provided
                String schemaName = getQueryParamValue(req, SCHEMA,
                        DEFAULT_SCHEMA);
                String blobFieldName = getQueryParamValue(req, BLOB_FIELD,
                        DEFAULT_BLOB_FIELD);
                String filenameFieldName = getQueryParamValue(req,
                        FILENAME_FIELD, DEFAULT_FILENAME_FIELD);
                blobPropertyName = schemaName + ":" + blobFieldName;
                filenameFieldName = schemaName + ":" + filenameFieldName;
            }

            InputStream is = req.getEntity().getStream();

            saveFileToDocument(filename, dm, blobPropertyName,
                    filenamePropertyName, is);
        } catch (Exception e) {
            handleError(res, e);
        }
    }

    protected CoreSession getDocumentManager() {
        return documentManager;
    }

    /**
     * Save the file into the document.
     */
    protected void saveFileToDocument(String filename, DocumentModel dm,
            String blobPropertyName, String filenamePropertyName, InputStream is)
            throws IOException, PropertyException, ClientException {
        // persisting the blob makes it possible to read the binary content
        // of the request stream several times (mimetype sniffing, digest
        // computation, core binary storage)
        Blob blob = StreamingBlob.createFromStream(is).persist();
        blob.setFilename(filename);

        dm.setPropertyValue(blobPropertyName, (Serializable) blob);
        dm.setPropertyValue(filenamePropertyName, filename);

        getDocumentManager().saveDocument(dm);
        getDocumentManager().save();
    }

}
