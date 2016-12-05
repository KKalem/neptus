package pt.lsts.neptus.plugins.multibeam.viewers;

import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import pt.lsts.imc.EstimatedState;
import pt.lsts.imc.SonarData;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.console.events.ConsoleEventMainSystemChange;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.multibeam.console.MultibeamRealTimeWaterfall;

import javax.swing.*;
import java.awt.*;

/**
 * Created by tsm on 05/12/16.
 */
@PluginDescription(author = "Tiago Marques", version = "0.1", name = "Multibeam: Dual Viewer", description = "Displays multibeam waterfall and cross-section viewers")
@Popup(pos = Popup.POSITION.TOP_LEFT, width = 1400, height = 800)
public class MultibeamDualViewer extends ConsolePanel {
    // GUI
    private JPanel viewersPanel;

    // data viewers
    private MultibeamCrossSection crossSection;
    private MultibeamRealTimeWaterfall waterfall;

    public MultibeamDualViewer(ConsoleLayout console) {
        super(console);
        crossSection = new MultibeamCrossSection(console);
        waterfall = new MultibeamRealTimeWaterfall(console);

        crossSection.mainVehicleChange(getMainVehicleId());
        waterfall.mainVehicleChange(getMainVehicleId());

        viewersPanel = new JPanel();
        viewersPanel.setLayout(new MigLayout());
        viewersPanel.setPreferredSize(new Dimension(this.getWidth(), this.getHeight()));
        viewersPanel.setLayout(new MigLayout());

        viewersPanel.add(waterfall, "w 30%, h 100%");
        viewersPanel.add(crossSection, "w 70%, h 100%");
        this.setLayout(new MigLayout("ins 0, gap 0", "[][]"));
        this.add(viewersPanel, "w 100%, h 100%,  grow");
    }

    @Subscribe
    public void onSonardata(SonarData msg) {
        new Thread(() -> crossSection.onSonarData(msg)).start();
        new Thread(() -> waterfall.onSonarData(msg)).start();
    }

    @Subscribe
    public void onEstimatedState(EstimatedState msg) {
        new Thread(() -> crossSection.onEstimatedState(msg)).start();
        new Thread(() -> waterfall.onEstimatedState(msg)).start();
    }


    @Override
    public void cleanSubPanel() {
        crossSection.cleanSubPanel();
        waterfall.cleanSubPanel();
    }

    @Override
    public void initSubPanel() {
        crossSection.initSubPanel();
        waterfall.initSubPanel();
    }
}
