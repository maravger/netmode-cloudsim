package org.cloudsimplus.examples;

/**
 * Created by avgr_m on 28/04/2017.
 */


import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import java.awt.*;

public class Plotter extends ApplicationFrame {

    public Plotter(final String title, final XYSeries seriesOfInterest, Color color) {
        super(title);
        XYPlot plot;
        final XYSeriesCollection data = new XYSeriesCollection(seriesOfInterest);
        final JFreeChart chart = ChartFactory.createXYLineChart(
            title,
            "Seconds",
            "",
            data,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        plot = (XYPlot)chart.getPlot();
        plot.getRenderer().setSeriesPaint(0, color);
        final ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(500, 270));
        setContentPane(chartPanel);
    }

    public Plotter(final String title, final XYDataset data, String yaxis) {

        super(title);
        final JFreeChart chart = createCombinedChart(data, yaxis, title);
        final ChartPanel panel = new ChartPanel(chart, true, true, true, false, true);
        panel.setPreferredSize(new java.awt.Dimension(500, 270));
        setContentPane(panel);

    }

    private static JFreeChart createCombinedChart(XYDataset data1, String yAxis, String title) {

        // create subplot 1...
        final XYItemRenderer renderer1 = new StandardXYItemRenderer();
        final NumberAxis rangeAxis1 = new NumberAxis(yAxis);
        final XYPlot subplot1 = new XYPlot(data1, null, rangeAxis1, renderer1);
        subplot1.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);

//        final XYTextAnnotation annotation = new XYTextAnnotation("Hello!", 50.0, 10000.0);
//        annotation.setFont(new Font("SansSerif", Font.PLAIN, 9));
//        annotation.setRotationAngle(Math.PI / 4.0);
//        subplot1.addAnnotation(annotation);

        // create subplot 2...
//        final XYItemRenderer renderer2 = new StandardXYItemRenderer();
//        final NumberAxis rangeAxis2 = new NumberAxis("Range 2");
//        rangeAxis2.setAutoRangeIncludesZero(false);
//        final XYPlot subplot2 = new XYPlot(data2, null, rangeAxis2, renderer2);
//        subplot2.setRangeAxisLocation(AxisLocation.TOP_OR_LEFT);

        // parent plot...
        final CombinedDomainXYPlot plot = new CombinedDomainXYPlot(new NumberAxis("Sample"));
        plot.setGap(10.0);

        // add the subplots...
        plot.add(subplot1, 1);
//        plot.add(subplot2, 1);
        plot.setOrientation(PlotOrientation.VERTICAL);

        // return a new chart containing the overlaid plot...
        return new JFreeChart(title,
            JFreeChart.DEFAULT_TITLE_FONT, plot, true);

    }
}
