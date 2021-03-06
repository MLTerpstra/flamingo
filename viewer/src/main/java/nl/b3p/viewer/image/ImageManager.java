/*
 * B3P Kaartenbalie is a OGC WMS/WFS proxy that adds functionality
 * for authentication/authorization, pricing and usage reporting.
 *
 * Copyright 2006, 2007, 2008 B3Partners BV
 * 
 * This file is part of B3P Kaartenbalie.
 * 
 * B3P Kaartenbalie is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * B3P Kaartenbalie is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with B3P Kaartenbalie.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.b3p.viewer.image;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * ImageManager definition:
 *
 */
public class ImageManager {

    private final Log log = LogFactory.getLog(this.getClass());
    private List ics = new ArrayList();
    private int maxResponseTime = 10000;
    
    protected static final String host = AuthScope.ANY_HOST;
    protected static final int port = AuthScope.ANY_PORT;

    public ImageManager(List urls, int maxResponseTime) {
        this(urls, maxResponseTime, null, null);
    }

    public ImageManager(List<CombineImageUrl> urls, int maxResponseTime, String uname, String pw) {
        this.maxResponseTime = maxResponseTime;
        if (urls == null || urls.size() <= 0) {
            return;
        }
        MultiThreadedHttpConnectionManager connectionManager = 
      		new MultiThreadedHttpConnectionManager();
        HttpClient client = new HttpClient(connectionManager);
        client.getHttpConnectionManager().
                getParams().setConnectionTimeout(this.maxResponseTime);

        if (uname != null && pw != null) {
            client.getParams().setAuthenticationPreemptive(true);
            Credentials defaultcreds = new UsernamePasswordCredentials(uname, pw);
            AuthScope authScope = new AuthScope(host, port);
            client.getState().setCredentials(authScope, defaultcreds);
        }
        for (CombineImageUrl ciu : urls) {
            ImageCollector ic = null;
            if (ciu instanceof CombineWmsUrl){
                ic = new ImageCollector(ciu, maxResponseTime, client, uname, pw);
            }else if (ciu instanceof CombineArcIMSUrl){
                ic = new ArcImsImageCollector(ciu, maxResponseTime,client);
            }else if (ciu instanceof CombineArcServerUrl){
                ic = new ArcServerImageCollector(ciu, maxResponseTime,client);
            }else {
                ic= new ImageCollector(ciu,maxResponseTime, client);
            }
            ics.add(ic);
        }
    }

    public void process() throws Exception {

        // Start threads
        ImageCollector ic = null;
        for (int i = 0; i < ics.size(); i++) {
            ic = (ImageCollector) ics.get(i);
            if (ic.getStatus() == ImageCollector.NEW) {
                ic.processNew();
            }
        }

        // Wait for all threads to be ready
        for (int i = 0; i < ics.size(); i++) {
            ic = (ImageCollector) ics.get(i);
            if (ic.getStatus() == ImageCollector.ACTIVE) {//if (ic.isAlive()) { /
                ic.processWaiting();
            }
        }
    }
    /**
     * Combine all the images recieved
     * @return a combined image
     * @throws Exception 
     */
    public List<ReferencedImage> getCombinedImages() throws Exception {
        ImageCollector ic = null;
        Iterator it = ics.iterator();
        List<ReferencedImage> allImages = new ArrayList<ReferencedImage>();
        while (it.hasNext()) {
            ic = (ImageCollector) it.next();
            int status = ic.getStatus();
            if (status == ImageCollector.ERROR || ic.getBufferedImage() == null) {
                log.error(ic.getMessage() + " (Status: " + status + ")");
            } else if (status != ImageCollector.COMPLETED) {
                // problem with one of sp's, but we continue with the rest!
                log.error(ic.getMessage() + " (Status: " + status + ")");
            } else {          
                ReferencedImage image =new ReferencedImage(ic.getBufferedImage());
                CombineImageUrl ciu = ic.getCombinedImageUrl();
                image.setAlpha(ciu.getAlpha());
                if (ciu instanceof CombineStaticImageUrl){
                    CombineStaticImageUrl csiu = (CombineStaticImageUrl)ciu;
                    image.setHeight(csiu.getHeight());
                    image.setWidth(csiu.getWidth());
                    image.setX(csiu.getX());
                    image.setY(csiu.getY());
                }
                allImages.add(image);
            }
        }
        if (allImages.size() > 0) {
            return allImages;
        } else {
            return null;
        }

    }
}
