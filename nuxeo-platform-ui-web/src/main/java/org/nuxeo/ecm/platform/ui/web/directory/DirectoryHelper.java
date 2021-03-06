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
 * $Id$
 */

package org.nuxeo.ecm.platform.ui.web.directory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.api.Framework;

/**
 * @author <a href="mailto:glefter@nuxeo.com">George Lefter</a>
 *
 */
public final class DirectoryHelper {

    private static final Log log = LogFactory.getLog(DirectoryHelper.class);

    /** Directory with a parent column. */
    public static final String XVOCABULARY_TYPE = "xvocabulary";

    public static final String[] VOCABULARY_TYPES = {"vocabulary",
            XVOCABULARY_TYPE };

    private static final String[] displayOptions = {"id", "label",
    "idAndLabel" };

    private static DirectoryHelper instance;

    private static DirectoryService service;

    private DirectoryHelper() {
    }

    public static DirectoryHelper instance() {
        if (null == instance) {
            instance = new DirectoryHelper();
        }
        return instance;
    }

    public boolean hasParentColumn(String directoryName) {
        try {
            return XVOCABULARY_TYPE.equals(getService()
                    .getDirectorySchema(directoryName));
        } catch (DirectoryException e) {
            // GR: our callers can't throw anything. Better to catch here, then
            log.error("Could not retrieve schema name for directory: "
                    + directoryName, e);
            return false;
        }
    }

    public DirectorySelectItem getSelectItem(String directoryName, Map<String, Serializable> filter) {
        List<DirectorySelectItem> items = getSelectItems(directoryName, filter);
        if (items.size() > 1) {
            log.warn("More than one entry found in directory " + directoryName + " and filter:");
            for (Map.Entry<String, Serializable> e: filter.entrySet()) {
                log.warn(String.format("%s=%s", e.getKey(), e.getValue()));
            }
        } else if (items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    public List<DirectorySelectItem> getSelectItems(String directoryName,
            Map<String, Serializable> filter) {
        return getSelectItems(directoryName, filter, Boolean.FALSE);
    }

    public List<DirectorySelectItem> getSelectItems(String directoryName,
            Map<String, Serializable> filter, Boolean localize) {
        List<DirectorySelectItem> list = new LinkedList<DirectorySelectItem>();

        Set<String> emptySet = Collections.emptySet();

        Map<String, String> orderBy = new LinkedHashMap<String, String>();

        FacesContext context = FacesContext.getCurrentInstance();

        // an extended schema also has parent field
        Session session = null;
        try {
            String schema = getService().getDirectorySchema(directoryName);
            session = getService().open(directoryName);
            if (session == null) {
                throw new ClientException("could not open session on directory: "
                        + directoryName);
            }

            // adding sorting support
            // XXX It seems that this ordering is obsolete as
            // DirectoryAwareComponent made it's own (NXP-7349)
            if (schema.equals(VOCABULARY_TYPES[0])
                    || schema.equals(VOCABULARY_TYPES[1])) {
                orderBy.put("ordering", "asc");
                orderBy.put("id", "asc");
            }

            DocumentModelList docModelList = session.query(filter, emptySet,
                    orderBy);

            for (DocumentModel docModel : docModelList) {
                String id = (String) docModel.getProperty(schema, "id");
                String label = (String) docModel.getProperty(schema, "label");
                long ordering = (Long)docModel.getProperty(schema, "ordering");

                if (Boolean.TRUE.equals(localize)) {
                    label = translate(context, label);
                }
                DirectorySelectItem item = new DirectorySelectItem(id, label, ordering);
                list.add(item);
            }

        } catch (ClientException e) {
            throw new RuntimeException(
                    "failed to build option list for directory "
                            + directoryName, e);
        } finally {
            try {
                if (session != null) {
                    session.close();
                }
            } catch (ClientException ce) {
                log.error("Could not close directory session", ce);
            }
        }

        return list;
    }

    public static DirectoryService getDirectoryService() {
        return instance().getService();
    }

    public static List<DirectorySelectItem> getSelectItems(
            VocabularyEntryList directoryValues, Map<String, Serializable> filter) {
        return getSelectItems(directoryValues, filter, Boolean.FALSE);
    }

    public static List<DirectorySelectItem> getSelectItems(
            VocabularyEntryList directoryValues, Map<String, Serializable> filter, Boolean localize) {
        List<DirectorySelectItem> list = new ArrayList<DirectorySelectItem>();

        // in obsolete filter we have either null (include obsoletes)
        // or 0 (don't include obsoletes)
        boolean obsolete = filter.get("obsolete") == null;
        String parentFilter = (String) filter.get("parent");

        FacesContext context = FacesContext.getCurrentInstance();
        for (VocabularyEntry entry : directoryValues.getEntries()) {
            if (!obsolete && Boolean.TRUE.equals(entry.getObsolete())) {
                continue;
            }
            String parent = entry.getParent();
            if (parentFilter == null) {
                if (parent != null) {
                    continue;
                }
            } else if (!parentFilter.equals(parent)) {
                continue;
            }

            String idValue = (String) filter.get("id");
            if (idValue != null && !idValue.equals(entry.getId())) {
                continue;
            }
            String id = entry.getId();
            String label = entry.getLabel();
            if (Boolean.TRUE.equals(localize)) {
                label = translate(context, label);
            }
            DirectorySelectItem item = new DirectorySelectItem(id, label);
            list.add(item);
        }
        return list;
    }

    public static String getOptionValue(String optionId, String optionLabel,
            String display, boolean displayIdAndLabel,
            String displayIdAndLabelSeparator) {
        StringBuilder displayValue = new StringBuilder();
        if (display != null && !"".equals(display)) {
            if (display.equals(displayOptions[0])) {
                displayValue.append(optionId);
            } else if (display.equals(displayOptions[1])) {
                displayValue.append(optionLabel);
            } else if (display.equals(displayOptions[2])) {
                displayValue.append(optionId).append(displayIdAndLabelSeparator).append(
                        optionLabel);
            } else {
                displayValue.append(optionLabel);
            }
        } else if (displayIdAndLabel) {
            displayValue.append(optionId).append(displayIdAndLabelSeparator).append(
                    optionLabel);
        } else {
            displayValue.append(optionLabel);
        }
        return displayValue.toString();
    }

    private static DocumentModel getEntryThrows(String directoryName, String entryId)
            throws ClientException {
        DirectoryService dirService = getDirectoryService();
        if (dirService == null) {
            throw new ClientException("Could not lookup DirectoryService");
        }
        Session session = dirService.open(directoryName);
        DocumentModel entry;
        try {
            entry = session.getEntry(entryId);
        } finally {
            try {
                session.close();
            } catch (ClientException e) {
                log.warn("Could not close a session on directory "
                        + directoryName, e);
            }
        }
        return entry;
    }

    /**
     * Returns the entry with given id from specified directory.
     * <p>
     * Method to use from components, since JSF base class that we extend don't allow
     * to throw proper exceptions.
     *
     * @param directoryName
     * @param entryId
     * @return the entry, or null in case of exception in callees.
     */
    public static DocumentModel getEntry(String directoryName, String entryId) {
        try {
            return getEntryThrows(directoryName, entryId);
        } catch (ClientException e) {
            log.error(String.format(
                    "Error retrieving the entry (dirname=%s, entryId=%s)",
                    directoryName, entryId), e);
            return null;
        }
    }

    protected static String translate(FacesContext context, String label) {
        String bundleName = context.getApplication().getMessageBundle();
        Locale locale = context.getViewRoot().getLocale();
        label = I18NUtils.getMessageString(bundleName, label, null, locale);
        return label;
    }

    protected DirectoryService getService() {
        if (service == null) {
            DirectoryService dirService = Framework.getLocalService(DirectoryService.class);
            if (dirService == null) {
                try {
                    dirService = Framework.getService(DirectoryService.class);
                } catch (Exception e) {
                    log.error("Can't find Directory Service", e);
                }
            } else {
                return dirService; // don't cache local service pointer
            }
            service = dirService;
        }
        return service;
    }

}
