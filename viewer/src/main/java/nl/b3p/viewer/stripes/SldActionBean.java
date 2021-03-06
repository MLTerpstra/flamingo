/*
 * Copyright (C) 2012-2013 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.b3p.viewer.stripes;

import java.io.*;
import java.net.URL;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.viewer.config.app.ApplicationLayer;
import nl.b3p.viewer.config.services.Layer;
import nl.b3p.viewer.config.services.SimpleFeatureType;
import nl.b3p.viewer.config.services.StyleLibrary;
import nl.b3p.viewer.util.ChangeMatchCase;
import nl.b3p.viewer.util.FeatureToJson;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.styling.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.filter.Filter;
import org.stripesstuff.stripersist.Stripersist;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Create a SLD using GeoTools.
 *
 * @author Matthijs Laan
 */
@UrlBinding("/action/sld")
@StrictBinding
public class SldActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(SldActionBean.class);
    
    private ActionBeanContext context;
    
    public static final String FORMAT_JSON = "json";
    public static final String FORMAT_XML = "xml";
    
    @Validate
    private Long id;
    
    @Validate
    private String layer;
    
    @Validate
    private String style;
    
    @Validate
    private String filter;
    
    @Validate
    private String featureTypeName;
    
    @Validate
    private String format;
    
    @Validate 
    private ApplicationLayer applicationLayer;
    
    private byte[] sldXml;
    private StyledLayerDescriptor newSld;
    private StyleFactory sldFactory;
    
    //<editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }
    
    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getFeatureTypeName() {
        return featureTypeName;
    }

    public void setFeatureTypeName(String featureTypeName) {
        this.featureTypeName = featureTypeName;
    }
    
    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
    
    public ApplicationLayer getApplicationLayer(){
        return applicationLayer;
    }
    
    public void setApplicationLayer(ApplicationLayer appLayer){
        this.applicationLayer=appLayer;
    }
    //</editor-fold>
    
    private void getSldXmlOrCreateNewSld() throws Exception {
        
        if(id != null) {
            StyleLibrary sld = Stripersist.getEntityManager().find(StyleLibrary.class, id);
            if(sld == null) {
                throw new IllegalArgumentException("Can't find SLD in Flamingo service registry with id " + id);
            }
            if(sld.getExternalUrl() == null) {
                sldXml = sld.getSldBody().getBytes("UTF8");
            } else {
                // retrieve external sld
                try {
                    InputStream externalSld = new URL(sld.getExternalUrl()).openStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    IOUtils.copy(externalSld, bos);
                    externalSld.close();
                    bos.flush();
                    bos.close();
                    sldXml = bos.toByteArray();
                } catch(IOException e) {
                    throw new IOException("Error retrieving external SLD from URL " + sld.getExternalUrl(), e);
                }
            }
        } else {
            // No SLD from database or external SLD; create new empty SLD

            newSld = sldFactory.createStyledLayerDescriptor();
            
            NamedLayer nl = sldFactory.createNamedLayer();
            nl.setName(layer);
            newSld.addStyledLayer(nl);
            if(style != null) {
                NamedStyle ns = sldFactory.createNamedStyle();
                ns.setName(style);
                nl.addStyle(ns);
            }
        }
    }
    
    private static final String NS_SLD = "http://www.opengis.net/sld";
    private static final String NS_SE = "http://www.opengis.net/se";
    
    private void addFilterToSld() throws Exception {
        Filter f = CQL.toFilter(filter);
        
        f = (Filter) f.accept(new ChangeMatchCase(false),null);
        
        if(featureTypeName == null) {
            featureTypeName = layer;
        }
        FeatureTypeConstraint ftc = sldFactory.createFeatureTypeConstraint(featureTypeName, f, new Extent[] {});
        
        if(newSld != null) {
            ((NamedLayer)newSld.getStyledLayers()[0]).setLayerFeatureConstraints(new FeatureTypeConstraint[] { ftc });
        } else {

            SLDTransformer sldTransformer = new SLDTransformer();             
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            sldTransformer.transform(ftc, bos);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            
            Document sldXmlDoc = db.parse(new ByteArrayInputStream(sldXml));
            
            Document ftcDoc = db.parse(new ByteArrayInputStream(bos.toByteArray()));
            
            String sldVersion = sldXmlDoc.getDocumentElement().getAttribute("version");
            if("1.1.0".equals(sldVersion)) {
                // replace sld:FeatureTypeName element generated by GeoTools
                // by se:FeatureTypeName
                NodeList sldFTNs = ftcDoc.getElementsByTagNameNS(NS_SLD, "FeatureTypeName");
                if(sldFTNs.getLength() == 1) {
                    Node sldFTN = sldFTNs.item(0);
                    Node seFTN = ftcDoc.createElementNS(NS_SE, "FeatureTypeName");
                    seFTN.setTextContent(sldFTN.getTextContent());
                    sldFTN.getParentNode().replaceChild(seFTN, sldFTN);
                }
            }

            // Ignore namespaces to tackle both SLD 1.0.0 and SLD 1.1.0
            // Add constraint to all NamedLayers, not only to the layer specified
            // in layers parameter
            
            NodeList namedLayers = sldXmlDoc.getElementsByTagNameNS(NS_SLD, "NamedLayer");
            for(int i = 0; i < namedLayers.getLength(); i++) {
                Node namedLayer = namedLayers.item(i);

                // Search where to insert the FeatureTypeConstraint from our ftcDoc
                
                // Insert LayerFeatureConstraints after sld:Name, se:Name or se:Description
                // and before sld:NamedStyle or sld:UserStyle so search backwards.
                // If we find an existing LayerFeatureConstraints, use that
                NodeList childs = namedLayer.getChildNodes();
                Node insertBefore = null;
                Node layerFeatureConstraints = null;
                int j = childs.getLength() - 1;
                do {
                    Node child = childs.item(j);
                    
                    if("LayerFeatureConstraints".equals(child.getLocalName())) {
                        layerFeatureConstraints = child;
                        break;
                    }
                    if("Description".equals(child.getLocalName()) || "Name".equals(child.getLocalName())) {
                        break;
                    }
                    insertBefore = child;
                    j--;
                } while(j >= 0);
                Node featureTypeConstraint = sldXmlDoc.adoptNode(ftcDoc.getDocumentElement().cloneNode(true));
                if(layerFeatureConstraints == null) {
                    layerFeatureConstraints = sldXmlDoc.createElementNS(NS_SLD, "LayerFeatureConstraints");
                    layerFeatureConstraints.appendChild(featureTypeConstraint);
                    namedLayer.insertBefore(layerFeatureConstraints, insertBefore);
                } else {
                    layerFeatureConstraints.appendChild(featureTypeConstraint);
                }
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            DOMSource source = new DOMSource(sldXmlDoc);
            bos =  new ByteArrayOutputStream();
            StreamResult result = new StreamResult(bos);
            t.transform(source, result);
            sldXml = bos.toByteArray();
        }
    }
    @DefaultHandler
    public Resolution create() throws JSONException, UnsupportedEncodingException {
        JSONObject json = new JSONObject();
        json.put("success", Boolean.FALSE);
        String error = null;

        try {
            sldFactory = CommonFactoryFinder.getStyleFactory();
            
            getSldXmlOrCreateNewSld();

            if(filter != null) {
                addFilterToSld();
            }
            
            if(newSld != null) {
                SLDTransformer sldTransformer = new SLDTransformer();             
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                sldTransformer.transform(newSld, bos);
                sldXml = bos.toByteArray();
            }
            
        } catch(Exception e) {
            log.error(String.format("Error creating sld for layer=%s, style=%s, filter=%s, id=%d",
                    layer,
                    style,
                    filter,
                    id), e);
            
            error = e.toString();
            if(e.getCause() != null) {
                error += "; cause: " + e.getCause().toString();
            }
        }
        
        if(error != null) {
            if(FORMAT_JSON.equals(format)) {
                json.put("error", error);
                return new StreamingResolution("application/json", new StringReader(json.toString()));                     
            } else {
                return new ErrorResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
            }
        } else {
            if(FORMAT_JSON.equals(format)) {
                json.put("sld", new String(sldXml, "UTF8"));
                json.put("success", Boolean.TRUE);
                return new StreamingResolution("application/json", new StringReader(json.toString()));
            } else {
                return new StreamingResolution("text/xml", new ByteArrayInputStream(sldXml));             
            }
        }
    }
    /*
     * Reformat the filter with the relations of this featureType
     */
    public Resolution transformFilter() throws JSONException{
        JSONObject json = new JSONObject();
        String error=null;
        try{
            json.put("success", Boolean.FALSE);
            if (filter!=null && applicationLayer!=null){
                Layer layer = applicationLayer.getService().getLayer(applicationLayer.getLayerName());
                if (layer==null){
                    error = "Layer not found";
                }else{
                    SimpleFeatureType sft=layer.getFeatureType();
                    Filter f = CQL.toFilter(filter);
                    f = (Filter) f.accept(new ChangeMatchCase(false), null);
                    f = FeatureToJson.reformatFilter(f, sft);
                    json.put("filter",CQL.toCQL(f));                
                    json.put("success", Boolean.TRUE);
                }
                
            }else{
                error="No filter to transform or no applicationlayer";
            }
        }catch(Exception e){
            log.error("Error while reformating filter",e);
            error = e.toString();
        }
        if (error!=null){
            json.put("error",error);
        }
        return new StreamingResolution("application/json",new StringReader(json.toString()));
    }
}
