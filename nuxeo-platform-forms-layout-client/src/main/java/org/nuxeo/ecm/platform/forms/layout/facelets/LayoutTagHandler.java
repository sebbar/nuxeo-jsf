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
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 * $Id: LayoutTagHandler.java 30553 2008-02-24 15:51:31Z atchertchian $
 */

package org.nuxeo.ecm.platform.forms.layout.facelets;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.faces.FacesException;
import javax.faces.component.UIComponent;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.forms.layout.api.Layout;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutDefinition;
import org.nuxeo.ecm.platform.forms.layout.service.WebLayoutManager;
import org.nuxeo.ecm.platform.ui.web.util.ComponentTagUtils;
import org.nuxeo.runtime.api.Framework;

import com.sun.facelets.FaceletContext;
import com.sun.facelets.FaceletHandler;
import com.sun.facelets.el.VariableMapperWrapper;
import com.sun.facelets.tag.TagAttribute;
import com.sun.facelets.tag.TagConfig;
import com.sun.facelets.tag.TagException;
import com.sun.facelets.tag.TagHandler;
import com.sun.facelets.tag.jsf.ComponentHandler;
import com.sun.facelets.tag.ui.IncludeHandler;

/**
 * Layout tag handler.
 * <p>
 * Computes a layout in given facelet context, for given mode and value
 * attributes. The layout can either be computed from a layout definition, or
 * by a layout name, where the layout service will lookup the corresponding
 * definition.
 * <p>
 * If a template is found for this layout, include the corresponding facelet
 * and use facelet template features to iterate over rows and widgets.
 * <p>
 * Since 5.6, the layout name attribute also accepts a comma separated list of
 * layout names.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class LayoutTagHandler extends TagHandler {

    private static final Log log = LogFactory.getLog(LayoutTagHandler.class);

    protected final TagConfig config;

    protected final TagAttribute name;

    /**
     * @since 5.5.
     */
    protected final TagAttribute category;

    /**
     * @since 5.4.2
     */
    protected final TagAttribute definition;

    protected final TagAttribute mode;

    protected final TagAttribute value;

    protected final TagAttribute template;

    protected final TagAttribute selectedRows;

    protected final TagAttribute selectedColumns;

    protected final TagAttribute selectAllByDefault;

    protected final TagAttribute[] vars;

    protected final String[] reservedVarsArray = { "id", "name", "category",
            "definition", "mode", "value", "template", "selectedRows",
            "selectedColumns", "selectAllByDefault" };

    public LayoutTagHandler(TagConfig config) {
        super(config);
        this.config = config;
        name = getAttribute("name");
        category = getAttribute("category");
        definition = getAttribute("definition");
        if (name == null && definition == null) {
            throw new TagException(this.tag,
                    "At least one of attributes 'name', or 'definition'"
                            + " is required");
        }
        mode = getRequiredAttribute("mode");
        value = getRequiredAttribute("value");
        template = getAttribute("template");
        selectedRows = getAttribute("selectedRows");
        selectedColumns = getAttribute("selectedColumns");
        if (selectedRows != null && selectedColumns != null) {
            throw new TagException(this.tag, "Attributes 'selectedRows' "
                    + "and 'selectedColumns' are aliases: only one of "
                    + "them should be filled");
        }
        selectAllByDefault = getAttribute("selectAllByDefault");
        vars = tag.getAttributes().getAll();
    }

    @SuppressWarnings("unchecked")
    // TODO: add javadoc about variables exposed
    public void apply(FaceletContext ctx, UIComponent parent)
            throws IOException, FacesException, ELException {
        WebLayoutManager layoutService;
        try {
            layoutService = Framework.getService(WebLayoutManager.class);
        } catch (Exception e) {
            throw new FacesException(e);
        }
        if (layoutService == null) {
            throw new FacesException("Layout service not found");
        }

        String modeValue = mode.getValue(ctx);
        String valueName = value.getValue();
        if (ComponentTagUtils.isStrictValueReference(valueName)) {
            valueName = ComponentTagUtils.getBareValueName(valueName);
        }

        List<String> selectedRowsValue = null;
        if (selectedRows != null || selectedColumns != null) {
            if (selectedRows != null) {
                selectedRowsValue = (List<String>) selectedRows.getObject(ctx,
                        List.class);
            } else if (selectedColumns != null) {
                selectedRowsValue = (List<String>) selectedColumns.getObject(
                        ctx, List.class);
            }
        }
        boolean selectAllByDefaultValue = false;
        if (selectAllByDefault != null) {
            selectAllByDefaultValue = selectAllByDefault.getBoolean(ctx);
        }

        String templateValue = null;
        if (template != null) {
            templateValue = template.getValue(ctx);
        }

        // add additional properties put on tag
        Map<String, Serializable> additionalProps = new HashMap<String, Serializable>();
        List<String> reservedVars = Arrays.asList(reservedVarsArray);
        for (TagAttribute var : vars) {
            String localName = var.getLocalName();
            if (!reservedVars.contains(localName)) {
                // resolve value as there's no alias value expression exposed
                // for layout properties
                additionalProps.put(localName, var.getValue(ctx));
            }
        }

        // expose some layout variables before layout creation so that they
        // can be used in mode expressions
        VariableMapper orig = ctx.getVariableMapper();
        VariableMapper vm = new VariableMapperWrapper(orig);
        ctx.setVariableMapper(vm);
        Map<String, ValueExpression> vars = getVariablesForLayoutBuild(ctx,
                modeValue);

        FaceletHandlerHelper helper = new FaceletHandlerHelper(ctx, config);
        try {
            for (Map.Entry<String, ValueExpression> var : vars.entrySet()) {
                vm.setVariable(var.getKey(), var.getValue());
            }

            if (name != null) {
                String layoutCategory = null;
                if (category != null) {
                    layoutCategory = category.getValue(ctx);
                }

                String nameValue = name.getValue(ctx);
                List<String> layoutNames = resolveLayoutNames(nameValue);
                for (String layoutName : layoutNames) {
                    Layout layoutInstance = layoutService.getLayout(ctx,
                            layoutName, layoutCategory, modeValue, valueName,
                            selectedRowsValue, selectAllByDefaultValue);
                    if (layoutInstance == null) {
                        String errMsg = String.format("Layout '%s' not found",
                                layoutName);
                        applyErrorHandler(ctx, parent, helper, errMsg);
                    } else {
                        applyLayoutHandler(ctx, parent, helper, layoutService,
                                layoutInstance, templateValue, additionalProps,
                                vars);
                    }
                }
            }

            if (definition != null) {
                LayoutDefinition layoutDef = (LayoutDefinition) definition.getObject(
                        ctx, LayoutDefinition.class);

                if (layoutDef == null) {
                    String errMsg = "Layout definition resolved to null";
                    applyErrorHandler(ctx, parent, helper, errMsg);
                } else {
                    Layout layoutInstance = layoutService.getLayout(ctx,
                            layoutDef, modeValue, valueName, selectedRowsValue,
                            selectAllByDefaultValue);
                    applyLayoutHandler(ctx, parent, helper, layoutService,
                            layoutInstance, templateValue, additionalProps,
                            vars);
                }

            }

        } finally {
            // layout resolved => cleanup variable mapper
            ctx.setVariableMapper(orig);
        }

    }

    /**
     * Resolves layouts names, splitting on character "," and trimming
     * resulting names, and allowing empty strings if the whole string is not
     * empty to ease up rendering of layout names using variables.
     * <p>
     * For instance, if value is null or empty, will return a single empty
     * layout name "". If value is "," it will return an empty list, triggering
     * no error for usage like <nxl:layout name="#{myLayout}, #{myOtherLayout}"
     * [...] />
     */
    protected List<String> resolveLayoutNames(String nameValue) {
        List<String> res = new ArrayList<String>();
        if (nameValue != null && nameValue.contains(",")) {
            // parse potential multiple layout names
            String[] names = nameValue.split(",");
            for (String layoutName : names) {
                if (!StringUtils.isBlank(layoutName)) {
                    layoutName = layoutName.trim();
                    res.add(layoutName);
                }
            }
        } else {
            if (nameValue == null) {
                nameValue = "";
            } else {
                nameValue = nameValue.trim();
            }
            res.add(nameValue);
        }
        return res;
    }

    protected void applyLayoutHandler(FaceletContext ctx, UIComponent parent,
            FaceletHandlerHelper helper, WebLayoutManager layoutService,
            Layout layoutInstance, String templateValue,
            Map<String, Serializable> additionalProps,
            Map<String, ValueExpression> vars) throws IOException,
            FacesException, ELException {

        // set unique id on layout
        layoutInstance.setId(helper.generateLayoutId(layoutInstance.getName()));

        // add additional properties put on tag
        if (additionalProps != null && !additionalProps.isEmpty()) {
            for (Map.Entry<String, Serializable> entry : additionalProps.entrySet()) {
                layoutInstance.setProperty(entry.getKey(), entry.getValue());
            }
        }

        if (StringUtils.isBlank(templateValue)) {
            templateValue = layoutInstance.getTemplate();
        }
        if (!StringUtils.isBlank(templateValue)) {
            final String layoutTagConfigId = layoutInstance.getTagConfigId();
            TagAttribute srcAttr = helper.createAttribute("src", templateValue);
            TagConfig config = TagConfigFactory.createTagConfig(this.config,
                    layoutTagConfigId,
                    FaceletHandlerHelper.getTagAttributes(srcAttr), nextHandler);
            FaceletHandler includeHandler = new IncludeHandler(config);

            // expose layout instance to variable mapper to ensure good
            // resolution of properties
            ExpressionFactory eFactory = ctx.getExpressionFactory();
            ValueExpression layoutVe = eFactory.createValueExpression(
                    layoutInstance, Layout.class);
            ctx.getVariableMapper().setVariable(
                    RenderVariables.layoutVariables.layout.name(), layoutVe);

            // expose all variables through an alias tag handler
            vars.putAll(getVariablesForLayoutRendering(ctx, layoutService,
                    layoutInstance));

            List<String> blockedPatterns = new ArrayList<String>();
            blockedPatterns.add(RenderVariables.layoutVariables.layout.name());
            blockedPatterns.add(RenderVariables.layoutVariables.layoutProperty.name()
                    + "_*");

            FaceletHandler handler = helper.getAliasTagHandler(
                    layoutTagConfigId, vars, blockedPatterns, includeHandler);

            // apply
            handler.apply(ctx, parent);
        } else {
            String errMsg = String.format(
                    "Missing template property for layout '%s'",
                    layoutInstance.getName());
            applyErrorHandler(ctx, parent, helper, errMsg);
        }
    }

    protected Map<String, ValueExpression> getVariablesForLayoutBuild(
            FaceletContext ctx, String modeValue) {
        Map<String, ValueExpression> vars = new HashMap<String, ValueExpression>();
        ValueExpression valueExpr = value.getValueExpression(ctx, Object.class);
        vars.put(RenderVariables.globalVariables.value.name(), valueExpr);
        vars.put(RenderVariables.globalVariables.document.name(), valueExpr);
        vars.put(RenderVariables.globalVariables.layoutValue.name(), valueExpr);
        ExpressionFactory eFactory = ctx.getExpressionFactory();
        ValueExpression modeVe = eFactory.createValueExpression(modeValue,
                String.class);
        vars.put(RenderVariables.globalVariables.layoutMode.name(), modeVe);
        // mode as alias to layoutMode
        vars.put(RenderVariables.globalVariables.mode.name(), modeVe);
        return vars;
    }

    /**
     * Computes variables for rendering, making available the layout instance
     * and its properties to the context.
     */
    protected Map<String, ValueExpression> getVariablesForLayoutRendering(
            FaceletContext ctx, WebLayoutManager layoutService,
            Layout layoutInstance) {
        Map<String, ValueExpression> vars = new HashMap<String, ValueExpression>();
        ExpressionFactory eFactory = ctx.getExpressionFactory();

        // expose layout value
        ValueExpression layoutVe = eFactory.createValueExpression(
                layoutInstance, Layout.class);
        vars.put(RenderVariables.layoutVariables.layout.name(), layoutVe);

        // expose layout properties too
        for (Map.Entry<String, Serializable> prop : layoutInstance.getProperties().entrySet()) {
            String key = prop.getKey();
            String name = String.format("%s_%s",
                    RenderVariables.layoutVariables.layoutProperty.name(), key);
            String value;
            Serializable valueInstance = prop.getValue();
            if (!layoutService.referencePropertyAsExpression(key,
                    valueInstance, null, null, null)) {
                // FIXME: this will not be updated correctly using ajax
                value = (String) valueInstance;
            } else {
                // create a reference so that it's a real expression and it's
                // not kept (cached) in a component value on ajax refresh
                value = String.format("#{%s.properties.%s}",
                        RenderVariables.layoutVariables.layout.name(), key);
            }
            vars.put(name,
                    eFactory.createValueExpression(ctx, value, Object.class));
        }

        return vars;
    }

    protected void applyErrorHandler(FaceletContext ctx, UIComponent parent,
            FaceletHandlerHelper helper, String message) throws IOException {
        log.error(message);
        ComponentHandler output = helper.getErrorComponentHandler(null, message);
        output.apply(ctx, parent);
    }

}
