/*
 * Copyright (C) 2011 B3Partners B.V.
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
package nl.b3p.viewer.admin.stripes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.annotation.security.RolesAllowed;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import nl.b3p.viewer.config.ClobElement;
import nl.b3p.viewer.config.app.ApplicationLayer;
import nl.b3p.viewer.config.security.Group;
import nl.b3p.viewer.config.services.ArcGISService;
import nl.b3p.viewer.config.services.ArcIMSService;
import nl.b3p.viewer.config.services.AttributeDescriptor;
import nl.b3p.viewer.config.services.BoundingBox;
import nl.b3p.viewer.config.services.Category;
import nl.b3p.viewer.config.services.CoordinateReferenceSystem;
import nl.b3p.viewer.config.services.FeatureSource;
import nl.b3p.viewer.config.services.GeoService;
import nl.b3p.viewer.config.services.Layer;
import nl.b3p.viewer.config.services.StyleLibrary;
import nl.b3p.viewer.config.services.TileService;
import nl.b3p.viewer.config.services.TileSet;
import nl.b3p.viewer.config.services.Updatable;
import nl.b3p.viewer.config.services.UpdateResult;
import nl.b3p.viewer.config.services.WMSService;
import nl.b3p.web.WaitPageStatus;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.*;
import org.geotools.filter.FilterTransformer;
import org.geotools.filter.text.cql2.CQL;
import org.json.*;
import org.opengis.filter.Filter;
import org.stripesstuff.plugin.waitpage.WaitPage;
import org.stripesstuff.stripersist.Stripersist;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Jytte Schaeffer
 */
@StrictBinding
@UrlBinding("/action/geoservice/{service}")
@RolesAllowed({Group.ADMIN, Group.REGISTRY_ADMIN})
public class GeoServiceActionBean implements ActionBean {

    private static final Log log = LogFactory.getLog(GeoServiceActionBean.class);
    private static final String JSP = "/WEB-INF/jsp/services/geoservice.jsp";
    private static final String JSP_EDIT_SLD = "/WEB-INF/jsp/services/editsld.jsp";    
    
    private ActionBeanContext context;
    @Validate(on = {"add"}, required = true)
    private Category category;
    @Validate(on = {"edit"}, required = true)
    private GeoService service;
    @Validate(on = "add", required = true)
    private String url;
    @Validate(on = "add", required = true)
    private String protocol;
    /**
     * Whether the service was succesfully deleted. Use in view JSP to update
     * tree.
     */
    private boolean serviceDeleted;
    @Validate
    private String name;
    @Validate
    private String username;
    @Validate
    private String password;
    @Validate
    private boolean overrideUrl;
    @Validate
    private String serviceName;
    @Validate
    private Integer tileSize;
    @Validate
    private String tilingProtocol;
    @Validate
    private String resolutions;
    @Validate
    private String serviceBbox;
    @Validate
    private String imageExtension;
    @Validate
    private String crs;
    private WaitPageStatus status;
    private JSONObject newService;
    private JSONObject updatedService;
    
    @Validate
    @ValidateNestedProperties({
            @Validate(on="saveSld",field="title", required=true),
            @Validate(on="saveSld",field="defaultStyle"),
            @Validate(on="saveSld",field="externalUrl"),
            @Validate(on="saveSld",field="sldBody"),
            @Validate(on="saveSld",field="extraLegendParameters")
    })
    private StyleLibrary sld;
    
    @Validate
    private String sldType = "external";
    
    @Validate(on="cqlToFilter")
    private String cql;
    
    private String generatedSld;
    
    private boolean updatable;

    //<editor-fold defaultstate="collapsed" desc="getters and setters">
    public ActionBeanContext getContext() {
        return context;
    }

    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public GeoService getService() {
        return service;
    }

    public void setService(GeoService service) {
        this.service = service;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public WaitPageStatus getStatus() {
        return status;
    }

    public void setStatus(WaitPageStatus status) {
        this.status = status;
    }

    public boolean isOverrideUrl() {
        return overrideUrl;
    }

    public void setOverrideUrl(boolean overrideUrl) {
        this.overrideUrl = overrideUrl;
    }

    public JSONObject getNewService() {
        return newService;
    }

    public void setNewService(JSONObject newService) {
        this.newService = newService;
    }

    public JSONObject getUpdatedService() {
        return updatedService;
    }

    public void setUpdatedService(JSONObject updatedService) {
        this.updatedService = updatedService;
    }

    public boolean isServiceDeleted() {
        return serviceDeleted;
    }

    public void setServiceDeleted(boolean serviceDeleted) {
        this.serviceDeleted = serviceDeleted;
    }

    public Integer getTileSize() {
        return tileSize;
    }

    public void setTileSize(Integer tileSize) {
        this.tileSize = tileSize;
    }

    public String getTilingProtocol() {
        return tilingProtocol;
    }

    public void setTilingProtocol(String tilingProtocol) {
        this.tilingProtocol = tilingProtocol;
    }

    public String getResolutions() {
        return resolutions;
    }

    public void setResolutions(String resolutions) {
        this.resolutions = resolutions;
    }

    public String getServiceBbox() {
        return serviceBbox;
    }

    public void setServiceBbox(String serviceBbox) {
        this.serviceBbox = serviceBbox;
    }

    public String getImageExtension() {
        return imageExtension;
    }

    public void setImageExtension(String imageExtension) {
        this.imageExtension = imageExtension;
    }

    public String getCrs() {
        return crs;
    }

    public void setCrs(String crs) {
        this.crs = crs;
    }

    public boolean isUpdatable() {
        return updatable;
    }

    public void setUpdatable(boolean updatable) {
        this.updatable = updatable;
    }

    public StyleLibrary getSld() {
        return sld;
    }

    public void setSld(StyleLibrary sld) {
        this.sld = sld;
    }

    public String getSldType() {
        return sldType;
    }

    public void setSldType(String sldType) {
        this.sldType = sldType;
    }

    public String getGeneratedSld() {
        return generatedSld;
    }

    public void setGeneratedSld(String generatedSld) {
        this.generatedSld = generatedSld;
    }

    public String getCql() {
        return cql;
    }

    public void setCql(String cql) {
        this.cql = cql;
    }
    //</editor-fold>
   
    
    @DefaultHandler
    public Resolution edit() {
        if (service != null) {
            protocol = service.getProtocol();
            url = service.getUrl();
            if (protocol.equals(ArcIMSService.PROTOCOL)) {
                ArcIMSService ser = (ArcIMSService) service;
                serviceName = ser.getServiceName();
            } else if (protocol.equals(TileService.PROTOCOL)) {
                TileService ser = (TileService) service;
                tilingProtocol = ser.getTilingProtocol();

                //tiling service has 1 layer with that has the settings.                
                Layer layer = ser.getTilingLayer();
                //set the resolutions

                TileSet tileSet = layer.getTileset();

                if (tileSet != null) {
                    String res = "";
                    for (Double resolution : tileSet.getResolutions()) {
                        if (res.length() > 0) {
                            res += ",";
                        }
                        res += resolution.toString();
                    }
                    resolutions = res;

                    //set the tilesize
                    tileSize = tileSet.getHeight();
                }

                //set the service Bbox               
                if (layer.getBoundingBoxes().size() == 1) {
                    BoundingBox bb = layer.getBoundingBoxes().values().iterator().next();
                    serviceBbox = "" + bb.getMinx() + ","
                            + bb.getMiny() + ","
                            + bb.getMaxx() + ","
                            + bb.getMaxy();
                    crs = bb.getCrs().getName();
                }
                serviceName = layer.getName();

                if (layer.getDetails().containsKey("image_extension")) {
                    ClobElement ce = layer.getDetails().get("image_extension");
                    imageExtension = ce != null ? ce.getValue() : null;
                }

            }
            name = service.getName();
            username = service.getUsername();
            password = service.getPassword();
        }
        return new ForwardResolution(JSP);
    }

    public Resolution save() {
        if (name != null) {
            service.setName(name);
        }
        if (service instanceof TileService) {
            TileService ser = (TileService) service;
            if (tilingProtocol != null) {
                ((TileService) service).setTilingProtocol(tilingProtocol);
            }
            if (url!=null){
                ((TileService) service).setUrl(url);
            }
            Layer l = ser.getTilingLayer();
            if (tileSize != null) {
                l.getTileset().setWidth(tileSize);
                l.getTileset().setHeight(tileSize);
            }
            if (resolutions != null) {
                l.getTileset().setResolutions(resolutions);
            }
            if (crs != null && serviceBbox != null) {
                BoundingBox bb = new BoundingBox();
                bb.setBounds(serviceBbox);
                bb.setCrs(new CoordinateReferenceSystem(crs));
                l.getBoundingBoxes().clear();
                l.getBoundingBoxes().put(bb.getCrs(), bb);
            }
            if (imageExtension != null) {
                l.getDetails().put("image_extension", new ClobElement(imageExtension));
            }

        }

        service.setUsername(username);
        service.setPassword(password);

        Stripersist.getEntityManager().persist(service);
        Stripersist.getEntityManager().getTransaction().commit();

        getContext().getMessages().add(new SimpleMessage("De service is opgeslagen"));

        return edit();
    }

    public Resolution delete() {
        /*
         * XXX Als een service layers heeft die toegevoegd zijn aan een
         * applicatie mag de service niet verwijderd worden
         */
        List<ApplicationLayer> applicationLayers = Stripersist.getEntityManager().createQuery("from ApplicationLayer where service = :service").setParameter("service", service).getResultList();
        if (applicationLayers.size() > 0) {
            serviceDeleted = false;

            getContext().getValidationErrors().addGlobalError(new SimpleError("Fout bij het verwijderen van de service. De service heeft kaartlagen geconfigureerd in {2} applicaties.", applicationLayers.size()));

            return edit();
        } else {
            Category c = service.getCategory();
            c.getServices().remove(service);

            List<FeatureSource> linkedSources = Stripersist.getEntityManager().createQuery(
                    "from FeatureSource where linkedService = :service").setParameter("service", service).getResultList();
            for (FeatureSource fs : linkedSources) {
                fs.setLinkedService(null);
                getContext().getMessages().add(
                        new SimpleMessage("De bij deze service automatisch aangemaakte attribuutbron \"{0}\" moet apart worden verwijderd", fs.getName()));

            }
            if (TileService.PROTOCOL.equals(service.getProtocol())) {
                if (service.getTopLayer() != null && service.getTopLayer().getTileset() != null) {
                    TileSet ts = service.getTopLayer().getTileset();
                    Stripersist.getEntityManager().remove(ts);
                }
            }

            Stripersist.getEntityManager().remove(service);
            Stripersist.getEntityManager().getTransaction().commit();

            serviceDeleted = true;

            getContext().getMessages().add(new SimpleMessage("De service is verwijderd"));
            return new ForwardResolution(JSP);
        }
    }
    
    @Before
    public void setUpdatable() {
        updatable = service instanceof Updatable;
    }
    
    public Resolution update() throws JSONException {
        if(!isUpdatable()) {
            getContext().getMessages().add(new SimpleMessage("Services van protocol {0} kunnen niet worden geupdate",
                    service.getProtocol()));
            return new ForwardResolution(JSP);
        }
        UpdateResult result = ((Updatable)service).update();
        
        if(result.getStatus() == UpdateResult.Status.FAILED) {
            getContext().getValidationErrors().addGlobalError(new SimpleError(result.getMessage()));
            return new ForwardResolution(JSP);
        }
        
        Map<UpdateResult.Status,List<String>> byStatus = result.getLayerNamesByStatus();
        
        log.info(String.format("Update layer stats: unmodified %d, updated %d, new %d, missing %d",
                byStatus.get(UpdateResult.Status.UNMODIFIED).size(),
                byStatus.get(UpdateResult.Status.UPDATED).size(),
                byStatus.get(UpdateResult.Status.NEW).size(),
                byStatus.get(UpdateResult.Status.MISSING).size()
        ));
        log.info("Unmodified layers: " + byStatus.get(UpdateResult.Status.UNMODIFIED));
        log.info("Updated layers: " + byStatus.get(UpdateResult.Status.UPDATED));
        log.info("New layers: " + byStatus.get(UpdateResult.Status.NEW));
        log.info("Missing layers: " + byStatus.get(UpdateResult.Status.MISSING));
                
        Stripersist.getEntityManager().getTransaction().commit();
        
        updatedService = new JSONObject();
        updatedService.put("id", "s" + service.getId());
        updatedService.put("name", service.getName());
        updatedService.put("type", "service");
        updatedService.put("isLeaf", service.getTopLayer() == null);
        updatedService.put("status", "ok");//Math.random() > 0.5 ? "ok" : "error");
        updatedService.put("parentid", "c" + category.getId());
        
        getContext().getMessages().add(new SimpleMessage("De service is geupdate"));
        
        return new ForwardResolution(JSP);
    }

    @ValidationMethod(on = "add")
    public void validateParams(ValidationErrors errors) {
        if (protocol.equals(ArcIMSService.PROTOCOL) || protocol.equals(TileService.PROTOCOL)) {
            if (serviceName == null) {
                errors.add("serviceName", new LocalizableError("validation.required.valueNotPresent"));
            }
            if (protocol.equals(TileService.PROTOCOL)) {
                if (resolutions == null) {
                    errors.add("resolutions", new LocalizableError("validation.required.valueNotPresent"));
                }
                if (serviceBbox == null) {
                    errors.add("serviceBbox", new LocalizableError("validation.required.valueNotPresent"));
                }
                if (crs == null) {
                    errors.add("crs", new LocalizableError("validation.required.valueNotPresent"));
                }
                if (tileSize == null) {
                    errors.add("tileSize", new LocalizableError("validation.required.valueNotPresent"));
                }
            }
        }


    }

    public Resolution addForm() {
        return new ForwardResolution(JSP);
    }

    @WaitPage(path = "/WEB-INF/jsp/waitpage.jsp", delay = 2000, refresh = 1000, ajax = "/WEB-INF/jsp/waitpageajax.jsp")
    public Resolution add() throws JSONException {

        status = new WaitPageStatus();

        Map params = new HashMap();

        try {
            if (protocol.equals(WMSService.PROTOCOL)) {
                params.put(WMSService.PARAM_OVERRIDE_URL, overrideUrl);
                params.put(WMSService.PARAM_USERNAME, username);
                params.put(WMSService.PARAM_PASSWORD, password);
                service = new WMSService().loadFromUrl(url, params, status);
            } else if (protocol.equals(ArcGISService.PROTOCOL)) {
                params.put(ArcGISService.PARAM_USERNAME, username);
                params.put(ArcGISService.PARAM_PASSWORD, password);
                service = new ArcGISService().loadFromUrl(url, params, status);
            } else if (protocol.equals(ArcIMSService.PROTOCOL)) {
                params.put(ArcIMSService.PARAM_SERVICENAME, serviceName);
                params.put(ArcIMSService.PARAM_USERNAME, username);
                params.put(ArcIMSService.PARAM_PASSWORD, password);
                service = new ArcIMSService().loadFromUrl(url, params, status);
            } else if (protocol.equals(TileService.PROTOCOL)) {
                params.put(TileService.PARAM_SERVICENAME, serviceName);
                params.put(TileService.PARAM_RESOLUTIONS, resolutions);
                params.put(TileService.PARAM_SERVICEBBOX, serviceBbox);
                params.put(TileService.PARAM_CRS, crs);
                params.put(TileService.PARAM_IMAGEEXTENSION, imageExtension);
                params.put(TileService.PARAM_TILESIZE, tileSize);
                params.put(TileService.PARAM_TILINGPROTOCOL, tilingProtocol);
                service = new TileService().loadFromUrl(url, params, status);
            } else {
                getContext().getValidationErrors().add("protocol", new SimpleError("Ongeldig"));
            }
        } catch (Exception e) {
            log.error("Exception loading " + protocol + " service from url " + url, e);
            String s = e.toString();
            if (e.getCause() != null) {
                s += "; cause: " + e.getCause().toString();
            }
            getContext().getValidationErrors().addGlobalError(new SimpleError("Fout bij het laden van de service: {2}", s));
            return new ForwardResolution(JSP);
        }

        if (name != null) {
            service.setName(name);
        }
        if (username != null) {
            service.setUsername(username);
        }
        if (password != null) {
            service.setPassword(password);
        }
        category = Stripersist.getEntityManager().find(Category.class, category.getId());
        service.setCategory(category);
        category.getServices().add(service);

        Stripersist.getEntityManager().persist(service);
        Stripersist.getEntityManager().getTransaction().commit();

        newService = new JSONObject();
        newService.put("id", "s" + service.getId());
        newService.put("name", service.getName());
        newService.put("type", "service");
        newService.put("isLeaf", service.getTopLayer() == null);
        newService.put("status", "ok");//Math.random() > 0.5 ? "ok" : "error");
        newService.put("parentid", "c" + category.getId());

        getContext().getMessages().add(new SimpleMessage("Service is ingeladen"));

        return edit();
    }

    @DontValidate
    public Resolution addSld() {
        return new ForwardResolution(JSP_EDIT_SLD);        
    }
    
    @Before(on="editSld")
    public void setSldType() {
        if(sld != null) {
            sldType = sld.getExternalUrl() != null ? "external" : "body";
        }
    }
    
    public Resolution editSld() {
        if(sld != null) {
            return new ForwardResolution(JSP_EDIT_SLD);        
        } else {
            return edit();
        }
    }
    
    public Resolution deleteSld() {
        if(sld != null) {
            service.getStyleLibraries().remove(sld);
            Stripersist.getEntityManager().remove(sld);
            Stripersist.getEntityManager().getTransaction().commit();
            getContext().getMessages().add(new SimpleMessage("SLD verwijderd"));
        }
        return edit();
    }
    
    @ValidationMethod(on="saveSld")
    public void validateSld() {
        if("external".equals(sldType) && StringUtils.isBlank(sld.getExternalUrl())) {
            getContext().getValidationErrors().add("sld.externalUrl", new LocalizableError("validation.required.valueNotPresent"));
            sld.setSldBody(null);
        }
        if("body".equals(sldType) && StringUtils.isBlank(sld.getSldBody())) {
            getContext().getValidationErrors().add("sld.sldBody", new LocalizableError("validation.required.valueNotPresent"));
            sld.setExternalUrl(null);
        }
    }
    
    private static final String NS_SLD = "http://www.opengis.net/sld";
    private static final String NS_OGC = "http://www.opengis.net/ogc";
    private static final String NS_GML = "http://www.opengis.net/gml";
    
    public Resolution generateSld() throws Exception {
        
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();        
        Document sldDoc = db.newDocument();
        
        Element sldEl = sldDoc.createElementNS(NS_SLD, "StyledLayerDescriptor");
        sldDoc.appendChild(sldEl);
        sldEl.setAttributeNS(NS_SLD, "version", "1.0.0");
        sldEl.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation", "http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd");
        sldEl.setAttribute("xmlns:ogc", NS_OGC);
        sldEl.setAttribute("xmlns:gml", NS_GML);
        service.loadLayerTree();
        
        Queue<Layer> layerStack = new LinkedList();
        Layer l = service.getTopLayer();
        while(l != null) {
            layerStack.addAll(service.getLayerChildrenCache(l));
            
            if(l.getName() != null) {
                Element nlEl = sldDoc.createElementNS(NS_SLD, "NamedLayer");
                sldEl.appendChild(nlEl);
                String title = l.getTitleAlias() != null ? l.getTitleAlias() : l.getTitle();
                if(title != null) {
                    nlEl.appendChild(sldDoc.createComment(" Layer '" + title + "' "));
                }
                Element nEl = sldDoc.createElementNS(NS_SLD, "Name");
                nEl.setTextContent(l.getName());
                nlEl.appendChild(nEl);
                
                if(l.getFeatureType() != null) {
                    String protocol = "";
                    if(l.getFeatureType().getFeatureSource() != null) {
                        protocol = " (protocol " + l.getFeatureType().getFeatureSource().getProtocol() + ")";
                    }
                    
                    String ftComment = " This layer has a feature type" + protocol + " you can use in a FeatureTypeConstraint element as follows:\n";
                    ftComment += "            <LayerFeatureConstraints>\n";
                    ftComment += "                <FeatureTypeConstraint>\n";
                    ftComment += "                    <FeatureTypeName>" + l.getFeatureType().getTypeName() + "</FeatureTypeName>\n";
                    ftComment += "                    Add ogc:Filter or Extent element here. ";
                    if(l.getFeatureType().getAttributes().isEmpty()) {
                        ftComment += " No feature type attributes are known.\n";
                    } else {
                        ftComment += " You can use the following feature type attributes in ogc:PropertyName elements:\n";
                        for(AttributeDescriptor ad: l.getFeatureType().getAttributes()) {
                            ftComment += "                    <ogc:PropertyName>" + ad.getName() + "</ogc:PropertyName>";
                            if(ad.getAlias() != null) {
                                ftComment += " (" + ad.getAlias() + ")";
                            }
                            if(ad.getType() != null) {
                                ftComment += " (type: " + ad.getType() + ")";
                            }
                            ftComment += "\n";
                        }
                    }
                    ftComment += "                </FeatureTypeConstraint>\n";
                    ftComment += "            </LayerFeatureConstraints>\n";
                    ftComment += "        ";
                    nlEl.appendChild(sldDoc.createComment(ftComment));
                }
                
                nlEl.appendChild(sldDoc.createComment(" Add a UserStyle or NamedStyle element here "));
                String styleComment = " (no server-side named styles are known other than 'default') ";
                ClobElement styleDetail = l.getDetails().get(Layer.DETAIL_WMS_STYLES);
                if(styleDetail != null) {
                    try {
                        JSONArray styles = new JSONArray(styleDetail.getValue());
                        
                        if(styles.length() > 0) {
                            styleComment = " The following NamedStyles are available according to the capabilities: \n";
                            
                            for(int i = 0; i < styles.length(); i++) {
                                JSONObject jStyle = styles.getJSONObject(i);
                                
                                styleComment += "            <NamedStyle><Name>" + jStyle.getString("name") + "</Name></NamedStyle>";
                                if(jStyle.has("title")) {
                                    styleComment += " (" + jStyle.getString("title") + ")";
                                }
                                styleComment += "\n";
                            }
                        }                        
                        
                    } catch(JSONException e) {
                    }
                    styleComment += "        ";
                }
                nlEl.appendChild(sldDoc.createComment(styleComment));
            }
            
            l = layerStack.poll();
        }
        
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        
        DOMSource source = new DOMSource(sldDoc);
        ByteArrayOutputStream bos =  new ByteArrayOutputStream();
        StreamResult result = new StreamResult(bos);
        t.transform(source, result);
        generatedSld = new String(bos.toByteArray(), "UTF-8");  
        
        // indent doesn't add newline after XML declaration
        generatedSld = generatedSld.replaceFirst("\"\\?><StyledLayerDescriptor", "\"?>\n<StyledLayerDescriptor");
        return new ForwardResolution(JSP_EDIT_SLD);  
    }
    
    @DontValidate
    public Resolution cqlToFilter() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("success", Boolean.FALSE);
        
        try {
            List<Filter> filters = CQL.toFilterList(cql);            
            
            FilterTransformer filterTransformer = new FilterTransformer();
            filterTransformer.setIndentation(4);
            filterTransformer.setOmitXMLDeclaration(true);
            filterTransformer.setNamespaceDeclarationEnabled(false);
            StringWriter sw = new StringWriter();
            for(Filter filter: filters) {
                sw.append('\n');
                filterTransformer.transform(filter, sw);
            }

            json.put("filter", sw.toString());
  
            json.put("success", Boolean.TRUE);
        } catch(Exception e) {
            String error = ExceptionUtils.getMessage(e);
            if(e.getCause() != null) {
                error += "; cause: " + ExceptionUtils.getMessage(e.getCause());
            }
            json.put("error", error);
        }
        return new StreamingResolution("application/json", new StringReader(json.toString()));                     
    }
    
    public Resolution validateSldXml() {
        Resolution jsp = new ForwardResolution(JSP_EDIT_SLD);  
        Document sldXmlDoc = null;
        String stage = "Fout bij parsen XML document";
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            
            sldXmlDoc = db.parse(new ByteArrayInputStream(sld.getSldBody().getBytes("UTF-8")));
            
            stage = "Fout bij controleren SLD";
            
            Element root = sldXmlDoc.getDocumentElement();
            if(!"StyledLayerDescriptor".equals(root.getLocalName())) {
                throw new Exception("Root element moet StyledLayerDescriptor zijn");
            }
            String version = root.getAttribute("version");
            if(version == null || !("1.0.0".equals(version) || "1.1.0".equals(version))) {
                throw new Exception("Geen of ongeldige SLD versie!");
            }
            
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema s = sf.newSchema(new URL("http://schemas.opengis.net/sld/" + version + "/StyledLayerDescriptor.xsd"));
            s.newValidator().validate(new DOMSource(sldXmlDoc));
                
        } catch(Exception e) {
            String extra = "";
            if(e instanceof SAXParseException) {
                SAXParseException spe = (SAXParseException)e;
                if(spe.getLineNumber() != -1) {
                    extra = " (regel " + spe.getLineNumber();
                    if(spe.getColumnNumber() != -1) {
                        extra += ", kolom " + spe.getColumnNumber();
                    }
                    extra += ")";
                }
            }
            getContext().getValidationErrors().addGlobalError(new SimpleError("{2}: {3}{4}",
                    stage,
                    ExceptionUtils.getMessage(e),
                    extra
            ));
            return jsp;
        }
        
        getContext().getMessages().add(new SimpleMessage("SLD is valide!"));
        
        return jsp;
    }
    
    public Resolution saveSld() {
        
        if(sld.getId() == null) {
            service.getStyleLibraries().add(sld);
        }
        
        if(sld.isDefaultStyle()) {
            for(StyleLibrary otherSld: service.getStyleLibraries()) {
                if(otherSld.getId() != null && !otherSld.getId().equals(sld.getId())) {
                    otherSld.setDefaultStyle(false);
                }
            }
        }

        try {
            sld.setNamedLayerUserStylesJson(null);
            InputSource sldBody = null;
            
            if(sld.getExternalUrl() == null) {
                sldBody = new InputSource(new StringReader(sld.getSldBody()));
            } else {
                sldBody = new InputSource(new URL(sld.getExternalUrl()).openStream());
            }
        
            sld.setNamedLayerUserStylesJson(StyleLibrary.parseSLDNamedLayerUserStyles(sldBody).toString(4));
        } catch(Exception e) {
            log.error("Fout bij bepalen UserStyle namen van NamedLayers", e);
            getContext().getValidationErrors().addGlobalError(new SimpleError("Kan geen namen van styles per layer bepalen: " + e.getClass().getName() + ": " + e.getLocalizedMessage()));
        }
        
        Stripersist.getEntityManager().getTransaction().commit();
        getContext().getMessages().add(new SimpleMessage("SLD opgeslagen"));
        return edit();
    }
}