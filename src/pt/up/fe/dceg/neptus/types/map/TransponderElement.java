/*
 * Copyright (c) 2004-2013 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: 
 * 15/Jan/2005
 */
package pt.up.fe.dceg.neptus.types.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Vector;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import pt.up.fe.dceg.neptus.NeptusLog;
import pt.up.fe.dceg.neptus.gui.objparams.ParametersPanel;
import pt.up.fe.dceg.neptus.gui.objparams.TransponderParameters;
import pt.up.fe.dceg.neptus.renderer2d.StateRenderer2D;
import pt.up.fe.dceg.neptus.types.Identifiable;
import pt.up.fe.dceg.neptus.types.coord.CoordinateSystem;
import pt.up.fe.dceg.neptus.types.coord.LocationType;
import pt.up.fe.dceg.neptus.types.misc.FileType;
import pt.up.fe.dceg.neptus.util.ImageUtils;
import pt.up.fe.dceg.neptus.util.PropertiesLoader;
import pt.up.fe.dceg.neptus.util.conf.ConfigFetch;

/**
 * Refactored in 06/11/2006.
 * 
 * @author Paulo Dias
 * @author Ze Carlos
 */
public class TransponderElement extends AbstractElement implements Identifiable{
    protected static final String DEFAULT_ROOT_ELEMENT = "transponder";
    private static Image transponderImg = ImageUtils.getImage("images/transponder.png");
    private static String[] transpondersListArray;

    static {
        final Vector<String> aTranspondersFiles = new Vector<String>();
        File dir = new File("maps/");
        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                // System.out.println(name + ": " +
                // name.matches("^(lsts[0-9]+\\.conf)|([A-Za-z][A-Za-z\\-_0-9]+\\.conf)$"));
                if (name.matches("^(lsts[0-9]+\\.conf)|([A-Za-z][A-Za-z0-9\\-\\_]*\\.conf)$")) {
                    return true;
                }
                return false;
            }
        });
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.getName().startsWith("lsts") && !o2.getName().startsWith("lsts"))
                    return -1;
                else if (!o1.getName().startsWith("lsts") && o2.getName().startsWith("lsts"))
                    return 1;
                return o1.compareTo(o2);
            }
        });
        for (File file : files) {
            // System.out.println(file.getName());
            aTranspondersFiles.add(file.getName());
        }
        transpondersListArray = aTranspondersFiles.toArray(new String[aTranspondersFiles.size()]);
    }

    protected FileType file = null;
    protected boolean buoyAttached = true;

    private TransponderParameters params = null;

    /**
     * The config file format is like the following:
     * 
     * <pre>
     * transponder delay (msecs.)=49.0
     * Responder lockout (secs)=1.0
     * interrogation channel=4
     * reply channel=3
     * </pre>
     * 
     * And is located maps directory acording to the file name indicated. Is load by calling {@link #setFile(FileType)}
     */
    private PropertiesLoader propConf = null;

    /**
     * 
     */
    public TransponderElement() {
        super();
    }

    /**
     * @param xml
     */
    public TransponderElement(String xml) {
        // super(xml);
        load(xml);
    }

    public TransponderElement(MapGroup mg, MapType parentMap) {
        super(mg, parentMap);
        if (mg != null)
            setCenterLocation(new LocationType(mg.getHomeRef().getCenterLocation()));
    }

    @Override
    public String getType() {
        return "Transponder";
    }

    @Override
    public boolean load(Element elem) {
        if (!super.load(elem))
            return false;

        try {
            // doc = DocumentHelper.parseText(xml);
            Node nd = doc.selectSingleNode("//file");
            if (nd != null)
                this.setFile(new FileType(nd.asXML()));
            nd = doc.selectSingleNode("//buoy-attached");
            if (nd != null)
                this.setBuoyAttached(new Boolean(nd.getText()).booleanValue());
        }
        catch (Exception e) {
            NeptusLog.pub().error(this, e);
            isLoadOk = false;
            return false;
        }
        isLoadOk = true;
        return true;
    }

    /**
     * @return Returns the file.
     */
    public FileType getFile() {
        return file;
    }

    /**
     * @param file The file to set.
     */
    public void setFile(FileType file) {
        this.file = file;

        try {
            propConf = new PropertiesLoader(ConfigFetch.resolvePath("maps/" + file.getHref()),
                    PropertiesLoader.PROPERTIES);
            fixPropertiesConfFormat();
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * To fix the fact that the property value can not have a space in the key.
     */
    private void fixPropertiesConfFormat() {
        Hashtable<String, String> fixedValues = new Hashtable<String, String>();
        for (Object keyO : propConf.keySet()) {
            String key = (String) keyO;
            String value = propConf.getProperty(key);
            String[] vs = value.split("=");
            if (vs.length > 1) {
                key = key + " " + vs[0];
                value = vs[1];
            }
            fixedValues.put(key, value);
        }
        propConf.clear();
        propConf.putAll(fixedValues);
    }

    public String getConfiguration() {
        if (getFile() == null)
            return null;

        return getFile().getHref();
    }

    public void setConfiguration(String configuration) {
        if (getFile() == null) {
            setFile(new FileType());
        }

        this.getFile().setHref(configuration);
        setFile(getFile());
    }

    /**
     * @return the propConf
     */
    public PropertiesLoader getPropConf() {
        return propConf;
    }

    /**
     * @return Returns the buoyAttached.
     */
    public boolean isBuoyAttached() {
        return buoyAttached;
    }

    /**
     * @param buoyAttached The buoyAttached to set.
     */
    public void setBuoyAttached(boolean buoyAttached) {
        this.buoyAttached = buoyAttached;
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.up.fe.dceg.neptus.types.XmlOutputMethods#asXML()
     */
    @Override
    public String asXML() {
        String rootElementName = DEFAULT_ROOT_ELEMENT;
        return asXML(rootElementName);
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.up.fe.dceg.neptus.types.XmlOutputMethods#asXML(java.lang.String)
     */
    @Override
    public String asXML(String rootElementName) {
        String result = "";
        Document document = asDocument(rootElementName);
        result = document.asXML();
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.up.fe.dceg.neptus.types.XmlOutputMethods#asElement()
     */
    @Override
    public Element asElement() {
        String rootElementName = DEFAULT_ROOT_ELEMENT;
        return asElement(rootElementName);
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.up.fe.dceg.neptus.types.XmlOutputMethods#asElement(java.lang.String)
     */
    @Override
    public Element asElement(String rootElementName) {
        return (Element) asDocument(rootElementName).getRootElement().detach();
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.up.fe.dceg.neptus.types.XmlOutputMethods#asDocument()
     */
    @Override
    public Document asDocument() {
        String rootElementName = DEFAULT_ROOT_ELEMENT;
        return asDocument(rootElementName);
    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.up.fe.dceg.neptus.types.XmlOutputMethods#asDocument(java.lang.String)
     */
    @Override
    public Document asDocument(String rootElementName) {
        Document document = DocumentHelper.createDocument();
        // Element root = super.asElement(DEFAULT_ROOT_ELEMENT);
        Element root = (Element) super.asDocument(DEFAULT_ROOT_ELEMENT).getRootElement().detach();
        document.add(root);

        // FIXME: Tratar disto
        root.add(getFile().asElement());
        root.addElement("buoy-attached").addText(new Boolean(isBuoyAttached()).toString());

        return document;
    }

    @Override
    public boolean containsPoint(LocationType lt, StateRenderer2D renderer) {
        double distance = getCenterLocation().getDistanceInMeters(lt);
        if ((distance * renderer.getZoom()) < 10)
            return true;

        return false;
    }

    @Override
    public int getLayerPriority() {
        return 9;
    }

    @Override
    public ParametersPanel getParametersPanel(boolean editable, MapType map) {

        if (params == null)
            params = new TransponderParameters(new CoordinateSystem());
        params.setMap(map != null ? map : getParentMap());
        params.setLocation(getCenterLocation());
        params.setConfiguration(getConfiguration());
        params.setHomeRef(getMapGroup().getCoordinateSystem());
        params.setEditable(editable);
        return params;
    }

    @Override
    public void initialize(ParametersPanel paramsPanel) {
        setCenterLocation(params.getLocationPanel().getLocationType());
        setConfiguration(params.getConfiguration());
    }

    @Override
    public void paint(Graphics2D g, StateRenderer2D renderer, double rotation) {
        Point2D tt = renderer.getScreenPosition(getCenterLocation());
        g.translate(tt.getX(), tt.getY());

        if (isSelected()) {
            g.setColor(Color.YELLOW);
            g.draw(new Rectangle2D.Double(-transponderImg.getWidth(renderer) / 2,
                    -transponderImg.getHeight(renderer) / 2, transponderImg.getWidth(null), transponderImg
                            .getHeight(null)));
        }

        g.drawImage(transponderImg, -transponderImg.getWidth(renderer) / 2, -transponderImg.getHeight(renderer) / 2,
                transponderImg.getWidth(null), transponderImg.getHeight(null), null);

        g.setColor(Color.WHITE);
        g.drawString(getName(), 7, 16);

    }

    @Override
    public ELEMENT_TYPE getElementType() {
        return ELEMENT_TYPE.TYPE_TRANSPONDER;
    }

    /**
     * 
     */
    public static final String[] getTranspondersListArray() {
        return transpondersListArray;
    }

    /* (non-Javadoc)
     * @see pt.up.fe.dceg.neptus.types.Identifiable#getIdentification()
     */
    @Override
    public String getIdentification() {
        return getName();
    }
}
