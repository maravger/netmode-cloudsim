package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.util.Log;
import org.jfree.data.xy.XYSeries;
import org.jfree.ui.RefineryUtilities;

import java.awt.*;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by avgr_m on 03/07/2017.
 */
public class SineTester {

    private static final XYSeries CLOUDLETS_ARRIVAL = new XYSeries("CLOUDLETS");

    private static final int TIME_TO_FINISH_SIMULATION = 1000;
    private static final int SAMPLING_INTERVAL = 30;
    private static final int MIN_CLOUDLETS_PERDELAY = 4;
    private static final int MAX_CLOUDLETS_PERDELAY = 8;
    private static final int INTERARRIVAL_DELAY_LOWER_BOUND = 1;
    private static final int INTERARRIVAL_DELAY_UPPER_BOUND = 3;

    public static void main(String[] args) {
        new SineTester();
        Color color = new Color((int)(Math.random() * 0x1000000));
        Plotter windowPlot = new Plotter("Cloudlets/Sec", CLOUDLETS_ARRIVAL, color.darker());
        windowPlot.pack();
        RefineryUtilities.positionFrameOnScreen(windowPlot, 0.5, 0.5);
        windowPlot.setVisible(true);
    }

    private SineTester() {
        createSinusoidCloudletArrival();
    }

    private void createSinusoidCloudletArrival() {
        int nofCloudletsToCreate;
        int secs = 0;
        while (secs<TIME_TO_FINISH_SIMULATION) {
            nofCloudletsToCreate = (int) Math.round(((MIN_CLOUDLETS_PERDELAY/2) * (Math.sin(secs*Math.PI/(SAMPLING_INTERVAL/2))+1)) + (MAX_CLOUDLETS_PERDELAY - MIN_CLOUDLETS_PERDELAY));
            //Log.printFormatted("nofCloudletsToCreate = " + nofCloudletsToCreate + "\n");
            CLOUDLETS_ARRIVAL.add(secs, nofCloudletsToCreate);
            secs += ThreadLocalRandom.current().nextInt(INTERARRIVAL_DELAY_LOWER_BOUND, INTERARRIVAL_DELAY_UPPER_BOUND);
        }
    }

}
