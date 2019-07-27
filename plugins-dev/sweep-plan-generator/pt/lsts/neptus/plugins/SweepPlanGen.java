package pt.lsts.neptus.plugins;

import com.l2fprod.common.propertysheet.DefaultProperty;
import com.l2fprod.common.propertysheet.Property;
import com.l2fprod.common.propertysheet.PropertySheetPanel;
import com.l2fprod.common.swing.renderer.DefaultCellRenderer;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.plugins.MainVehicleChangeListener;
import pt.lsts.neptus.console.plugins.planning.edit.AllManeuversPayloadSettingsChanged;
import pt.lsts.neptus.data.Pair;
import pt.lsts.neptus.gui.PropertiesEditor;
import pt.lsts.neptus.gui.PropertiesProvider;
import pt.lsts.neptus.gui.PropertiesTable;
import pt.lsts.neptus.gui.editor.renderer.I18nCellRenderer;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.mp.ManeuverLocation;
import pt.lsts.neptus.mp.actions.PlanActions;
import pt.lsts.neptus.mp.maneuvers.*;
import pt.lsts.neptus.params.ConfigurationManager;
import pt.lsts.neptus.params.ManeuverPayloadConfig;
import pt.lsts.neptus.params.SystemProperty;
import pt.lsts.neptus.params.renderer.I18nSystemPropertyRenderer;
import pt.lsts.neptus.renderer2d.InteractionAdapter;
import pt.lsts.neptus.renderer2d.Renderer2DPainter;
import pt.lsts.neptus.renderer2d.StateRenderer2D;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.coord.PolygonType;
import pt.lsts.neptus.types.map.PlanElement;
import pt.lsts.neptus.types.map.PlanUtil;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.types.vehicle.VehiclesHolder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.xml.stream.Location;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.util.*;
import java.util.List;

@PluginDescription(name = "Sweep Plan Generator")
public class SweepPlanGen extends InteractionAdapter implements Renderer2DPainter {

    private JPanel sidePanel = null;
    private PropertySheetPanel propsPanel = null;
    private PropertiesTable propTable = null;
    private HashMap<Component, Object> componentList = new HashMap<>();

    private MultiVehicleDynamicSurveyOptions generalOptions = new MultiVehicleDynamicSurveyOptions();
    private PropertiesProvider generalProvider = null;
    private LinkedHashMap<String, SystemProperty> vehicleOptions = new LinkedHashMap<>();

    private String vehicle = null;
    private double sweepAngle = -Math.PI/180;

    //MAP INTERACTION
    private PolygonType task = null;
    private PolygonType selectedTask = null;
    private PolygonType.Vertex startPoint = null;
    private PolygonType.Vertex endPoint = null;
    private PlanType generated = null;
    private PlanElement planElement = null;
    private StateRenderer2D stateRenderer = null;

    public SweepPlanGen(ConsoleLayout console) {
        super(console);
    }

    @Override
    public void setActive(boolean mode, StateRenderer2D source) {
        super.setActive(mode, source);
        stateRenderer = source;

        JSplitPane horizontalSplit;
        if (mode) {
            Container c = source;
            while (c.getParent() != null && !(c.getLayout() instanceof BorderLayout))
                c = c.getParent();
            if (c.getLayout() instanceof BorderLayout) {
                componentList.clear();
                BorderLayout bl = (BorderLayout) c.getLayout();
                for (Component component : c.getComponents()) {
                    Object constraint = bl.getConstraints(component);
                    componentList.put(component, constraint);
                }

                Component comp = bl.getLayoutComponent(BorderLayout.CENTER);

                horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, comp, getSidePanel());
                horizontalSplit.setResizeWeight(1.0);

                c.add(horizontalSplit);

                c.invalidate();
                c.validate();
                if (c instanceof JComponent)
                    ((JComponent) c).setBorder(new LineBorder(Color.orange.darker(), 3));
            }
        } else {
            Container c = source;
            while (c.getParent() != null && !(c.getLayout() instanceof BorderLayout))
                c = c.getParent();
            if (c.getLayout() instanceof BorderLayout) {
                // c.remove(getSidePanel());
                c.removeAll();
                for (Map.Entry<Component, Object> e : componentList.entrySet()) {
                    c.add(e.getKey(), e.getValue());
                }
                componentList.clear();

                c.invalidate();
                c.validate();
                if (c instanceof JComponent)
                    ((JComponent) c).setBorder(new EmptyBorder(0, 0, 0, 0));
            }
        }
    }

    private JPanel getSidePanel() {

        if (sidePanel == null) {
            sidePanel = new JPanel(new BorderLayout(2, 2));

            sidePanel.add(getAngleSelector(),BorderLayout.SOUTH);
            sidePanel.add(getOptionsPanel(),BorderLayout.CENTER);
            sidePanel.add(getVehicleSelector(),BorderLayout.PAGE_START);
        }
        return sidePanel;
    }

    private JComboBox<String> getVehicleSelector() {
        JComboBox<String> vehicleList = new JComboBox<>(VehiclesHolder.getVehiclesArray());

        // add listener for main console vehicle change
        getConsole().addMainVehicleListener(id -> vehicleList.getModel().setSelectedItem(id));

        vehicleList.addItemListener(e -> {
            if(e.getStateChange() != ItemEvent.SELECTED){
                return;
            }
            setVehicle((String)e.getItem());
            updateProperties();
            updateVehiclePropsConstraints();
            getConsole().setMainSystem((String)e.getItem());
        });

        // set vehicle local variable
        setVehicle(getMainVehicleId());

        // set combo box selected vehicle
        vehicleList.getModel().setSelectedItem(getMainVehicleId());

        return vehicleList;
    }

    private JPanel getAngleSelector() {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("Sweep Angle");
        panel.add(label);

        JSpinner angleSpinner;
        SpinnerNumberModel angleSpinnerModel;
        Double current = (double) -1;
        Double min = (double) -1;
        Double max = 365d;
        Double step = 1d;
        angleSpinnerModel = new SpinnerNumberModel(current, min, max, step);
        angleSpinner = new JSpinner(angleSpinnerModel);
        angleSpinner.addChangeListener(evt -> {
            double newValue = (double)((JSpinner)evt.getSource()).getValue();
            sweepAngle = newValue * Math.PI/180;
            updatePlan(stateRenderer);
        });
        ((JSpinner.DefaultEditor)angleSpinner.getEditor()).getTextField().setColumns(5);

        panel.add(angleSpinner);
        label.setLabelFor(angleSpinner);
        return panel;
    }

    private JPanel getOptionsPanel() {
        JPanel optionsPanel = new JPanel();

        optionsPanel.setLayout(new GridLayout(2,0));

        optionsPanel.add(getGeneralPropertiesTable());

        optionsPanel.add(getVehiclePropertiesTable());

        return optionsPanel;
    }

    private PropertySheetPanel getVehiclePropertiesTable() {
        propsPanel = new PropertySheetPanel();
        propsPanel.setDescriptionVisible(true);
        propsPanel.setMode(PropertySheetPanel.VIEW_AS_CATEGORIES);
        propsPanel.addPropertySheetChangeListener(evt -> {
            if (evt.getSource() instanceof SystemProperty) {
                SystemProperty sp = (SystemProperty) evt.getSource();
                sp.setValue(evt.getNewValue());

                for (SystemProperty sprop : vehicleOptions.values()) {
                    sprop.propertyChange(evt);
                }
                sp.propertyChange(evt);
            }

            updateVehiclePropsConstraints();
            //System.out.println(categoryName+"."+propName+" : "+evt.getNewValue());
        });

        propsPanel.setEditorFactory(PropertiesEditor.getPropertyEditorRegistry());
        propsPanel.setRendererFactory(PropertiesEditor.getPropertyRendererRegistry());
        propsPanel.setToolBarVisible(false);

        updateProperties();

        propsPanel.setBorder(new TitledBorder(I18n.text("Vehicle Properties")));


        return propsPanel;
    }

    private PropertiesTable getGeneralPropertiesTable() {
        generalProvider = new PropertiesProvider() {
            @Override
            public DefaultProperty[] getProperties() {
                return PluginUtils.getPluginProperties(generalOptions);
            }

            @Override
            public void setProperties(Property[] properties) {
                PluginUtils.setPluginProperties(generalOptions, properties);
            }

            @Override
            public String getPropertiesDialogTitle() {
                return PluginUtils.getPluginName(generalOptions.getClass());
            }

            @Override
            public String[] getPropertiesErrors(Property[] properties) {
                return null;
            }
        };

        propTable = new PropertiesTable();
        propTable.editProperties(generalProvider);
        propTable.addPropertyChangeListener(evt -> {
            System.out.println("PropTable Change Listener");
            updatePlan(stateRenderer);
        });

        propTable.setBorder(new TitledBorder(I18n.text("General Properties")));
        return propTable;
    }

    @SuppressWarnings("deprecation")
    private void updateProperties() {
        vehicleOptions.clear();
        ArrayList<SystemProperty> payloadProps = ConfigurationManager.getInstance().getClonedProperties(vehicle, SystemProperty.Visibility.USER,
                SystemProperty.Scope.MANEUVER);

        // Add vehicle props to the PropertySheetPanel
        for (SystemProperty sp : payloadProps) {
            sp.resetToDefault();

            String name = sp.getName();
            vehicleOptions.put(sp.getCategoryId() + "." + name, sp);

            if (propsPanel != null) {
                if (sp.getEditor() != null) {
                    propsPanel.getEditorRegistry().registerEditor(sp, sp.getEditor());
                }
                if (sp.getRenderer() != null) {
                    DefaultCellRenderer rend;
                    if(sp.getRenderer() instanceof I18nSystemPropertyRenderer) {
                        I18nSystemPropertyRenderer rendSProp = (I18nSystemPropertyRenderer) sp.getRenderer();
                        I18nCellRenderer newRend = new I18nCellRenderer(rendSProp.getUnitsStr());
                        newRend.setI18nMapper(rendSProp.getI18nMapper());
                        rend = newRend;
                        propsPanel.getRendererRegistry().registerRenderer(sp, rend);
                    }
                }
            }
        }

        // Let us make sure all dependencies between properties are ok
        for (SystemProperty spCh : vehicleOptions.values()) {
            for (SystemProperty sp : vehicleOptions.values()) {
                PropertyChangeEvent evt = new PropertyChangeEvent(spCh, spCh.getName(), null, spCh.getValue());
                sp.propertyChange(evt);
            }
        }

        if(propsPanel != null){
            propsPanel.setProperties(payloadProps.toArray(new SystemProperty[0]));
        }
    }

    private void updateVehiclePropsConstraints(){
        vehicleOptions.forEach((String key,SystemProperty property)->{
            String[] catNamePair = key.split("\\.");
            System.out.println(Arrays.toString(catNamePair));
            String categoryName = catNamePair[0];
            String propName = catNamePair[1];

            double newValue = 0;

            if(propTable != null && generalProvider != null && isRangeProperty(categoryName,propName)){
                if((propName.contains("Multiplier") || propName.contains("Frequency")) && vehicleOptions.get("Sidescan.Range")==null){
                    String lowChannels = (String)vehicleOptions.get("Sidescan.Low-Frequency Channels").getValue();
                    String highChannels = (String)vehicleOptions.get("Sidescan.High-Frequency Channels").getValue();

                    double lowRange = 0;
                    double highRange = 0;
                    if(!lowChannels.equals("None")){
                        if(vehicleOptions.get("Sidescan.Low-Frequency Range").getValue() instanceof Long){
                            lowRange = ((Long)vehicleOptions.get("Sidescan.Low-Frequency Range").getValue()).doubleValue();
                        } else {
                            lowRange = ((Integer)vehicleOptions.get("Sidescan.Low-Frequency Range").getValue()).doubleValue();
                        }
                    }
                    if(!highChannels.equals("None")){
                        highRange = ((Long)vehicleOptions.get("Sidescan.High-Frequency Range").getValue()).doubleValue();
                    }

                    newValue = Math.max(lowRange,highRange);
                } else if(propName.equals("Range")){
                    newValue = ((Long)property.getValue()).doubleValue();
                }
                if(newValue != 0) {
                    generalOptions.swathWidth = newValue*2;
                    generalOptions.depth = -newValue*0.1;
                    propTable.editProperties(generalProvider);
                    updatePlan(stateRenderer);
                }
            }
        });
    }

    private boolean isRangeProperty(String categoryName, String propName) {
        return (propName.contains("Range") || propName.contains("Frequency")) && categoryName.equals("Sidescan");
    }

    //MAP INTERACTION
    private LocationType lastPoint = null;
    private PolygonType.Vertex clickedVertex = null;

    private boolean containsPoint(LocationType lt, StateRenderer2D renderer){
        if (task.containsPoint(lt))
            return true;

        Point2D screen = renderer.getScreenPosition(lt);
        for (PolygonType.Vertex v : task.getVertices()) {
            Point2D pt = renderer.getScreenPosition(v.getLocation());

            if (pt.distance(screen) < 10) {
                return true;
            }
        }
        return false;
    }

    /*private double calculateAzimuth(PolygonType poly, LocationType lt) {
        LocationType center = task.getCentroid();
        double[] offset = lt.getOffsetFrom(center);
        System.out.println(Arrays.toString(offset));
        double realAzimuth = Math.atan2(offset[0],offset[1]);
        if(realAzimuth < 0){
            return 2*Math.PI + realAzimuth;
        } else {
            return realAzimuth;
        }
    }*/

    private double distSq(double lx1, double ly1, double lz1, double lx2, double ly2, double lz2){
        return Math.pow(lx1-lx2,2)+ Math.pow(ly1-ly2,2) + Math.pow(lz1-lz2,2);
    }

    private double calculatePointProjectionDist(double[] linePoint1, double[] linePoint2, double[] lt) {
        double lx1 = linePoint1[0];
        double ly1 = linePoint1[1];
        double lz1 = linePoint1[2];
        double lx2 = linePoint2[0];
        double ly2 = linePoint2[1];
        double lz2 = linePoint2[2];
        double px = lt[0];
        double py = lt[1];
        double pz = lt[2];

        double line_dist = distSq(lx1, ly1, lz1, lx2, ly2, lz2);
        if (line_dist == 0) return distSq(px, py, pz, lx1, ly1, lz1);
        double t = ((px - lx1) * (lx2 - lx1) + (py - ly1) * (ly2 - ly1) + (pz - lz1) * (lz2 - lz1)) / line_dist;
        t = Math.min(1, Math.max(t, 0));
        return distSq(px, py, pz, lx1 + t * (lx2 - lx1), ly1 + t * (ly2 - ly1), lz1 + t * (lz2 - lz1));
    }

    private int getVertexOptimalIndex(PolygonType poly, LocationType lt) {
        List<PolygonType.Vertex> vertices = poly.getVertices();
        if(vertices.size() < 4){
            // returning index for the next vertex
            return vertices.size();
        }

        double minDist = Double.MAX_VALUE;
        double currDist;
        int optimalIndex = 0;

        PolygonType.Vertex currVertex = vertices.get(0);
        for (int i = 1; i < vertices.size(); i++) {
            /*currDist += currVertex.getLocation().getDistanceInMeters(lt);
            currDist += lt.getDistanceInMeters(vertices.get(i).getLocation());*/

            currDist = calculatePointProjectionDist(task.getCentroid().getOffsetFrom(currVertex.getLocation()),
                    task.getCentroid().getOffsetFrom(vertices.get(i).getLocation()),
                    task.getCentroid().getOffsetFrom(lt));

            System.out.println(i + ". Curr Distance: "+currDist);
            if(currDist < minDist){
                minDist = currDist;
                optimalIndex = i;
            }
            currVertex = vertices.get(i);
        }

        // last to first check
        /*currDist += currVertex.getLocation().getDistanceInMeters(lt);
        currDist += lt.getDistanceInMeters(vertices.get(0).getLocation());*/
        currDist = calculatePointProjectionDist(task.getCentroid().getOffsetFrom(currVertex.getLocation()),
                task.getCentroid().getOffsetFrom(vertices.get(0).getLocation()),
                task.getCentroid().getOffsetFrom(lt));

        System.out.println("Last. Curr Distance: "+currDist);
        if(currDist < minDist){
            optimalIndex = vertices.size();
        }

        /*TreeMap<Double, LocationType> map = new TreeMap<>();
        List<PolygonType.Vertex> vertices = new ArrayList<>(poly.getVertices());
        vertices.forEach((PolygonType.Vertex vertex) -> {
            LocationType tempLt = vertex.getLocation();
            map.put(calculateAzimuth(task,tempLt),tempLt);
        });
        System.out.println(map.toString());*/

        return optimalIndex;
    }

    private PolygonType.Vertex getVertexAt(StateRenderer2D renderer, PolygonType poly, LocationType lt){
        Point2D screen = renderer.getScreenPosition(lt);
        for (PolygonType.Vertex v : poly.getVertices()) {
            Point2D pt = renderer.getScreenPosition(v.getLocation());
            if (pt.distance(screen) < 10) {
                return v;
            }
        }
        return null;
    }

    private boolean isVertex(StateRenderer2D renderer, LocationType lt, PolygonType.Vertex vertex) {
        Point2D screen = renderer.getScreenPosition(lt);
        Point2D pt = renderer.getScreenPosition(vertex.getLocation());
        return pt.distance(screen) < 10;
    }

    @Override
    public void mouseClicked(MouseEvent e, StateRenderer2D source) {
        if (!SwingUtilities.isRightMouseButton(e)) {
            super.mouseClicked(e, source);
            return;
        }

        PolygonType survey = null;
        LocationType lt = source.getRealWorldLocation(e.getPoint());

        if (task != null && task.containsPoint(lt)) {
            survey = task;
        }

        JPopupMenu popup = new JPopupMenu();

        if (startPoint != null && isVertex(source, lt, startPoint)) {
           popup.add("<html><b>Remove</b> start point").addActionListener(e1 -> {
               startPoint = null;
               updatePlan(source);
           });
        }

        if (endPoint != null && isVertex(source, lt, endPoint)) {
            popup.add("<html><b>Remove</b> end point").addActionListener(e12 -> {
                endPoint = null;
                updatePlan(source);
            });
        }

        if(survey != null) {
            popup.add("<html><b>Delete</b> Survey").addActionListener(evt -> {
                task = null;
                endPoint = null;
                startPoint = null;
                planElement = null;
            });
        }

        if(task != null) {
            // check if a polygon vertex was hit
            PolygonType.Vertex selectedVertex = getVertexAt(source, task, source.getRealWorldLocation(e.getPoint()));
            if (selectedVertex != null && task.getVertices().size() > 3) {
                popup.add("<html><b>Remove</b> vertex").addActionListener(e13 -> {
                    task.removeVertex(selectedVertex);
                    task.recomputePath();
                    updatePlan(source);
                });
            }

            popup.add("<html>Add <b>Vertex</b>").addActionListener(evt -> {
                int optimalIndex = getVertexOptimalIndex(task,source.getRealWorldLocation(e.getPoint()));
                //task.addVertex(source.getRealWorldLocation(e.getPoint()));
                task.addVertex(optimalIndex,source.getRealWorldLocation(e.getPoint()));
                task.recomputePath();
                updatePlan(source);
            });
            popup.add("<html>Add <b>Start</b> Point").addActionListener(evt -> {
                startPoint = new PolygonType.Vertex(source.getRealWorldLocation(e.getPoint()));
                updatePlan(source);
            });
            popup.add("<html>Add <b>End</b> Point").addActionListener(evt -> {
                endPoint = new PolygonType.Vertex(source.getRealWorldLocation(e.getPoint()));
                updatePlan(source);
            });
        } else {
            popup.add("<html>New <b>Survey</b>").addActionListener(evt -> {
                // ADD NEW SURVEY
                task = new PolygonType();
                task.setColor(Color.red);

                LocationType clickedLocation = source.getRealWorldLocation(e.getPoint());
                LocationType nw = new LocationType(clickedLocation), ne = new LocationType(clickedLocation),
                        sw = new LocationType(clickedLocation), se = new LocationType(clickedLocation);

                nw.translatePosition(60, -60, 0);
                ne.translatePosition(60, 60, 0);
                sw.translatePosition(-60, -60, 0);
                se.translatePosition(-60, 60, 0);
                task.addVertex(nw);
                task.addVertex(ne);
                task.addVertex(se);
                task.addVertex(sw);
                task.recomputePath();
                planElement = new PlanElement();
                updatePlan(source);
            });
            popup.add("<html>Test Function").addActionListener(evt -> {
                // TODO: 01/02/2019 remove this
            });
        }

        if(task != null && generated != null) {
            popup.add("<html><b>Save</b> Plan to Mission").addActionListener(e14 -> savePlan());
        }

        popup.show(source, e.getX(), e.getY());
    }

    @Override
    public void mousePressed(MouseEvent event, StateRenderer2D source) {
        if(task != null && endPoint != null && isVertex(source,source.getRealWorldLocation(event.getPoint()),endPoint)){
            selectedTask = task;
            lastPoint = source.getRealWorldLocation(event.getPoint());
            clickedVertex = endPoint;
            return;
        }
        if(task != null && startPoint != null && isVertex(source,source.getRealWorldLocation(event.getPoint()),startPoint)){
            selectedTask = task;
            lastPoint = source.getRealWorldLocation(event.getPoint());
            clickedVertex = startPoint;
            return;
        }
        if (task != null && containsPoint(source.getRealWorldLocation(event.getPoint()),source)) {
            selectedTask = task;
            lastPoint = source.getRealWorldLocation(event.getPoint());

            for (int i = 0; i < task.getVertices().size(); i++) {
                PolygonType.Vertex v = task.getVertices().get(i);
                Point2D pt = source.getScreenPosition(v.getLocation());
                if (pt.distance(event.getPoint()) < 15) {
                    clickedVertex = v;
                    return;
                }
            }

            clickedVertex = null;
        } else {
            super.mousePressed(event, source);
        }
    }

    @Override
    public void mouseDragged(MouseEvent event, StateRenderer2D source) {
        if(selectedTask == null){
            super.mouseDragged(event,source);
            return;
        }

        if (clickedVertex != null) {
            clickedVertex.setLocation(source.getRealWorldLocation(event.getPoint()));
            task.recomputePath();
        } else if (lastPoint != null) {
            LocationType loc = source.getRealWorldLocation(event.getPoint());
            double offsets[] = loc.getOffsetFrom(lastPoint);
            task.translate(offsets[0], offsets[1]);
        }

        updatePlan(source);

        lastPoint = source.getRealWorldLocation(event.getPoint());
    }

    @Override
    public void mouseReleased(MouseEvent event, StateRenderer2D source) {
        if(selectedTask != null) {
            lastPoint = null;
            clickedVertex = null;

            updatePlan(source);
        }

        selectedTask = null;
        super.mouseReleased(event, source);
    }

    @Override
    public void paint(Graphics2D g, StateRenderer2D renderer) {
        if(task != null && planElement != null && isActive()) {
            task.paint(g,renderer);
            g.setTransform(renderer.getIdentity());
            for (PolygonType.Vertex v : task.getVertices()) {
                Point2D pt = renderer.getScreenPosition(v.getLocation());
                g.fill(new Ellipse2D.Double(pt.getX() - 5, pt.getY() - 5, 10, 10));
            }
            if(startPoint != null){
                Point2D pt = renderer.getScreenPosition(startPoint.getLocation());
                g.setColor(Color.GREEN);
                g.fill(new Ellipse2D.Double(pt.getX()-5, pt.getY()-5,10,10));
            }
            if(endPoint != null){
                Point2D pt = renderer.getScreenPosition(endPoint.getLocation());
                g.setColor(Color.GREEN);
                g.fill(new Ellipse2D.Double(pt.getX()-5, pt.getY()-5,10,10));
            }
            planElement.paint(g,renderer);
        }
    }

    private ManeuverLocation createLoc(LocationType loc) {
        ManeuverLocation manLoc;
        manLoc = new ManeuverLocation(loc);
        if (generalOptions.depth >= 0) {
            manLoc.setZ(generalOptions.depth);
            manLoc.setZUnits(ManeuverLocation.Z_UNITS.DEPTH);
        }
        else {
            manLoc.setZ(-generalOptions.depth);
            manLoc.setZUnits(ManeuverLocation.Z_UNITS.ALTITUDE);
        }

        return manLoc;
    }

    private void addStartManeuver(){
        if (startPoint == null)
            return;

        LocationType lastLoc;

        Goto start = new Goto();
        start.setId("Start");
        lastLoc = new ManeuverLocation(startPoint.getLocation());
        ManeuverLocation mloc = createLoc(lastLoc);
        mloc.setZ(0);
        mloc.setZUnits(ManeuverLocation.Z_UNITS.DEPTH);
        start.setManeuverLocation(mloc);
        generated.getGraph().addManeuverAtEnd(start);
    }

    private void addEndManeuver(LocationType lastLoc){
        if (endPoint == null)
            return;

        PopUp manP = new PopUp();
        manP.setId("P_End");
        manP.setManeuverLocation(createLoc(lastLoc));
        manP.setDuration(generalOptions.popupDuration);
        generated.getGraph().addManeuverAtEnd(manP);

        Goto end = new Goto();
        end.setId("Go_End");
        end.setManeuverLocation(createLoc(endPoint.getLocation()));
        end.getManeuverLocation().setAbsoluteDepth(0);
        generated.getGraph().addManeuverAtEnd(end);

        lastLoc = new ManeuverLocation(endPoint.getLocation());
        StationKeeping manSK = new StationKeeping();
        manSK.setId("SK_End");
        ManeuverLocation mloc = createLoc(lastLoc);
        mloc.setZ(0);
        mloc.setZUnits(ManeuverLocation.Z_UNITS.DEPTH);
        manSK.setManeuverLocation(mloc);
        manSK.setDuration(0);
        generated.getGraph().addManeuverAtEnd(manSK);
        manSK.getManeuverLocation().setAbsoluteDepth(0);
    }

    private void generatePlan() {

        Pair<Double, Double> diamAngle = task.getDiameterAndAngle();
        double angle = sweepAngle != -Math.PI/180 ? sweepAngle : diamAngle.second();

        int corner = generalOptions.corner;

        /*for (int i = 0; i<4 ; i++){
            System.out.println("Length for i = "+i+" : " + task.getPathLength(generalOptions.swathWidth,i));
            //System.out.println("Better Length for i = "+i+" : " + getPathLength(task.getCoveragePath(angle, generalOptions.swathWidth, corner)));
        }*/

        generated = new PlanType(getConsole().getMission());

        int manId = 1;
        LocationType lastLoc;
        long curTime = System.currentTimeMillis() + generalOptions.startInMins * 60_000;


        double shortestDistance = Double.MAX_VALUE;


        //restrict overlap range
        double validOverlap = Math.max(0,generalOptions.overlap/100);
        validOverlap = Math.min(1,validOverlap);

        double finalSwathWidth = generalOptions.swathWidth - (generalOptions.swathWidth*validOverlap*0.5);

        // Find Shortest Distance Corner
        for (int i = 0; i < 4; i++) {
            ArrayList<LocationType> tempCoverage = task.getCoveragePath(angle, finalSwathWidth, i);
            LocationType covStart = tempCoverage.get(0);
            LocationType covEnd = tempCoverage.get(tempCoverage.size()-1);
            double distance = 0;
            // add distance to start point
            if(startPoint != null){
                distance += covStart.getDistanceInMeters(startPoint.getLocation());
            }

            // add distance to end point
            if(endPoint != null){
                distance += covEnd.getDistanceInMeters(endPoint.getLocation());
            }
            distance += task.getPathLength(angle, finalSwathWidth, i);
            //System.out.println("Better Length for i = "+i+" : " + distance);
            if(distance < shortestDistance){
                shortestDistance = distance;
                corner = i;
            }
        }

        //System.out.println("Selected Corner: "+corner+" with total distance: "+shortestDistance);

        if(generalOptions.corner != -1)
            corner = generalOptions.corner;

        ArrayList<LocationType> coverage = task.getCoveragePath(angle, finalSwathWidth, corner);

        if (generalOptions.reversed)
            Collections.reverse(coverage);

        addStartManeuver();

        if (generalOptions.timedPlan) {
            ScheduledGoto m1 = new ScheduledGoto();
            m1.setId("SG"+manId++);
            m1.setManeuverLocation(createLoc(coverage.remove(0)));
            m1.setArrivalTime(new Date(curTime));
            generated.getGraph().addManeuverAtEnd(m1);
            lastLoc = new LocationType(m1.getManeuverLocation());
        }
        else {
            Goto m1 = new Goto();
            m1.setId("Go"+manId++);
            lastLoc = new ManeuverLocation(coverage.remove(0));
            ManeuverLocation mloc = createLoc(lastLoc);
            mloc.setZ(0);
            mloc.setZUnits(ManeuverLocation.Z_UNITS.DEPTH);
            m1.setManeuverLocation(mloc);
            generated.getGraph().addManeuverAtEnd(m1);
            m1.setManeuverLocation(mloc);
            m1.getManeuverLocation().setAbsoluteDepth(0);
            lastLoc = new LocationType(m1.getManeuverLocation());

            PopUp man = new PopUp();
            man.setId("P"+manId++);
            man.setManeuverLocation(createLoc(lastLoc));
            man.setDuration(generalOptions.popupDuration);
            generated.getGraph().addManeuverAtEnd(man);
        }
        long lastPopup = System.currentTimeMillis();

        FollowTrajectory traj = null;

        while(!coverage.isEmpty()) {
            LocationType loc = coverage.remove(0);

            double distanceToTarget = lastLoc.getDistanceInMeters(loc);
            long targetEta = (long) ((distanceToTarget / generalOptions.speedMps) * 1000 + curTime);

            if ((targetEta - lastPopup)/60_000.0 > generalOptions.popupMins) {
                if (traj != null)
                    generated.getGraph().addManeuverAtEnd(traj);
                traj = null;

                //add popup
                PopUp man = new PopUp();
                man.setId("P"+manId++);
                ManeuverLocation mloc = createLoc(lastLoc);
                man.setManeuverLocation(mloc);
                man.setDuration(generalOptions.popupDuration);
                man.setWaitAtSurface(generalOptions.popupWaitAtSurface);
                generated.getGraph().addManeuverAtEnd(man);
                lastPopup = curTime + generalOptions.popupDuration * 1_000;
                targetEta += generalOptions.popupDuration * 1_000;

            }

            if (traj == null) {
                if (generalOptions.timedPlan) {
                    traj = new FollowTrajectory();
                    traj.setId("FT"+manId++);
                }
                else {
                    traj = new FollowPath();
                    traj.setId("FP"+manId++);
                }
                traj.setManeuverLocation(createLoc(lastLoc));
                Vector<double[]> curPath = new Vector<>();
                curPath.add(new double[] {0, 0, 0, 0});
                traj.setOffsets(curPath);

            }

            Vector<double[]> curPath = new Vector<>(traj.getPathPoints());
            double[] offsets = loc.getOffsetFrom(traj.getManeuverLocation());
            curPath.add(new double[] {offsets[0], offsets[1], offsets[2], (targetEta - curTime) / 1000.0});
            traj.setOffsets(curPath);
            lastLoc = loc;
            curTime = targetEta;
        }

        if (traj != null)
            generated.getGraph().addManeuverAtEnd(traj);

        if(endPoint != null){
            addEndManeuver(lastLoc);
        } else {
            StationKeeping man = new StationKeeping();
            man.setId("SK"+manId);
            ManeuverLocation mloc = createLoc(lastLoc);
            mloc.setZ(0);
            mloc.setZUnits(ManeuverLocation.Z_UNITS.DEPTH);
            man.setManeuverLocation(mloc);
            man.setDuration(0);
            generated.getGraph().addManeuverAtEnd(man);
            man.getManeuverLocation().setAbsoluteDepth(0);
        }

        PlanUtil.setPlanSpeed(generated, generalOptions.speedMps);

        generated.setId(generalOptions.planId);
        planElement.setPlan(generated);
    }

    private void setPayloads()  {
        if(generated == null || vehicle == null)
            return;

        Goto pivot = new Goto();

        ManeuverPayloadConfig payloadConfig = new ManeuverPayloadConfig(vehicle, pivot, propsPanel);

        payloadConfig.setProperties(propsPanel.getProperties());

        PlanActions newPlanActions = pivot.getStartActions();

        AllManeuversPayloadSettingsChanged undoRedo = new AllManeuversPayloadSettingsChanged(generated, newPlanActions, null);
        undoRedo.redo();
    }

    private void setVehicle(String vehicle) {
        this.vehicle = vehicle;
        if(task != null){
            generated.setVehicle(vehicle);
        }
    }

    private void updatePlan(StateRenderer2D renderer) {
        if(task == null)
            return;
        generatePlan();
        planElement.recalculateManeuverPositions(renderer);
    }

    private void savePlan() {
        setVehicle(vehicle);
        setPayloads();
        getConsole().getMission().addPlan(generated);
        getConsole().getMission().save(true);
        getConsole().warnMissionListeners();
    }

    public static class MultiVehicleDynamicSurveyOptions {
        @NeptusProperty(name="Swath Width", description="Cross-track region covered by each vehicle")
        double swathWidth = 180;

        @NeptusProperty(name="Depth", description= "Depth at which to travel (negative for altitude)")
        double depth = 4;

        @NeptusProperty(name="Speed (m/s)", description="Speed to use while travelling")
        double speedMps = 1.2;

        @NeptusProperty(name="Minutes till first point", description="Amount of minutes to travel to the first waypoint")
        int startInMins = 1;

        @NeptusProperty(name="Create timed plan", description="Opt to generate desired ETA for each waypoint")
        boolean timedPlan = false;

        @NeptusProperty(name="Popup periodicity in minutes", description="Do not stay underwater more than this time (minutes)")
        int popupMins = 30;

        @NeptusProperty(name="Popup duration in seconds", description="How long to stay at surface when the vehicle pops up")
        int popupDuration = 45;

        @NeptusProperty(name="Popup Wait at surface", description="If set, the vehicle will wait <duration> seconds before diving, otherwise will dive after GPS fix.")
        boolean popupWaitAtSurface = true;

        @NeptusProperty(name="Generated plan id", description="Name of the generated plan")
        String planId = "plan_wiz";

        @NeptusProperty(name="Reversed", description="Reverse plan")
        boolean reversed = false;

        @NeptusProperty(name="Corner", description="First Corner")
        int corner = -1;

        @NeptusProperty(name="Sweep Overlap", description="Percentage of overlapping coverage", units = "Values should be between 0(no overlap) and 100(full overlap)")
        double overlap = 10;
    }
}