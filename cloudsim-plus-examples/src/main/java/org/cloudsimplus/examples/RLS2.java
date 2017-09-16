package org.cloudsimplus.examples;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RefineryUtilities;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by avgr_m on 05/07/2017.
 */
public class RLS2 {

    public static void main(String[] args) {
//        matrixExample();
//        checkIfWorking(300);
//        double data[][] = CSVUtils.readTo2dArray("/Users/avgr_m/Desktop/CSVs/Vm0@2017.07.11.13.05.28.csv", true);
//        RealMatrix rm = new Array2DRowRealMatrix(data);
//        System.out.println(Arrays.toString(rm.getColumn(1))); //art
//        System.out.println(Arrays.toString(rm.getColumn(2))); //cpuutil
//        double[] theta = compute(rm.getColumn(1), rm.getColumn(2));
//        System.out.println(Arrays.toString(theta));

        double data1[][] = CSVUtils.readTo2dArray("/Users/avgr_m/Desktop/CSVs/Vm0_@2017.07.26.12.49.31_100Samples.csv", true);
        double data2[][] = CSVUtils.readTo2dArray("/Users/avgr_m/Desktop/CSVs/Vm0_@2017.07.26.12.47.57_100Samples.csv", true);
        RealMatrix rm = new Array2DRowRealMatrix(data1);
        RealMatrix rm2 = new Array2DRowRealMatrix(data2);
        double[] x = rm.getColumn(1);
        double[][] u2 = {rm2.getColumn(4),rm2.getColumn(7)};
        double[][] u1 = {rm.getColumn(4),rm.getColumn(7)};
        System.out.println("BFR: " + computebfr(x, rm2.getColumn(1), u1, u2) + "%");
//        System.out.println(Arrays.toString(compute(x, u)));
//        checkIfWorking(10000);
    }

    public static double[] compute(double[] x, double[][] u) {
        int inputs = u.length;
        int samples = x.length;

//        System.out.println("Inputs = " + inputs);
        // init f
        RealMatrix f = new Array2DRowRealMatrix(new double[inputs+1][samples]);
//        System.out.println("Rows = " + f.getRowDimension());
        RealMatrix rmx = new Array2DRowRealMatrix(x);
        rmx = rmx.transpose();
//        print2dMatrix(rmx);
        RealMatrix rmu = new Array2DRowRealMatrix(u);
//        print2dMatrix(rmu);

//        f.setRowVector(0, rmx.getRowVector(0));
        f.setRow(0,rmx.getRow(0));
        for (int r = 1; r < inputs+1; r++) {
            f.setRowVector(r, rmu.getRowVector(r-1));
        }
        //print2dMatrix(f);

        return doTheRLS(samples, inputs, f);
    }

    public static double[] compute(double[] x, double[] u) {
        int inputs = 1;
        int samples = x.length;

        double[][] temp_f = {x,u};
        RealMatrix f = new Array2DRowRealMatrix(temp_f);

        return doTheRLS(samples, inputs,f);
    }

    public static double[] doTheRLS(int samples, int inputs, RealMatrix f) {
        RealMatrix f_col, identM, ffp, fpf, pfe, thNewMatr;

        //init plot arrays
//        final XYSeries alpha = new XYSeries("a");
//        final XYSeries beta = new XYSeries("b");
//        final XYSeries gamma = new XYSeries("c");

        final XYSeries[] vars = new XYSeries[inputs+1];
        for (int q = 0; q < inputs+1; q++) {
            vars[q] = new XYSeries(q);
        }

        //init error
        double e;

        // init theta
        double[][] temp_th = new double[inputs+1][samples];
        for (double[] row: temp_th)
            Arrays.fill(row, 0.0);
        for (int k = 0; k < inputs+1; k++) {
            temp_th[k][0] = ThreadLocalRandom.current().nextDouble(0, 1); // uniform
        }
//        temp_th[1][0] = ThreadLocalRandom.current().nextDouble(-0.7,-0.3);
        RealMatrix th = new Array2DRowRealMatrix(temp_th);

        // init p
        RealMatrix p = MatrixUtils.createRealIdentityMatrix(inputs+1);
        p = p.scalarMultiply(10000);

        for (int i = 1; i < samples; i++) {
            f_col = f.getColumnMatrix(i-1);
            e = f.getEntry(0,i) - (f_col.transpose().multiply(th.getColumnMatrix(i-1))).getEntry(0,0);
            identM = MatrixUtils.createRealIdentityMatrix(inputs+1);
            ffp = f_col.multiply(f_col.transpose()).multiply(p);
            fpf = f_col.transpose().multiply(p).multiply(f_col);
            p = p.multiply(identM.subtract(ffp.scalarMultiply(1/(1 + fpf.getEntry(0,0)))));
            pfe = p.multiply(f_col).scalarMultiply(e);
            thNewMatr = th.getColumnMatrix(i-1).add(pfe);
            th.setColumnMatrix(i, thNewMatr);
            for (int q = 0; q < inputs+1; q++) {
                vars[q].add(i,th.getEntry(q,i));
            }
            //System.out.println(th.getEntry(1,i));
        }

        createMixedPlot(vars, "Theta", "Value");
        return th.getColumn(samples-1);
    }

    private static void matrixExample() {
        double[][] matrixData2 = { {1d,2d}, {2d,5d}, {1d, 7d}};
        double[][] matrixData = { {1d,2d,3d}, {2d,5d,3d}};
        RealMatrix m = MatrixUtils.createRealMatrix(matrixData);
        RealMatrix n = new Array2DRowRealMatrix(matrixData2);
        RealMatrix p = m.multiply(n);
        System.out.println(p.getRowDimension());    // 2
        System.out.println(p.getColumnDimension()); // 2
        for(int i = 0; i < p.getRowDimension(); i++)
        {
            for(int j = 0; j < p.getColumnDimension(); j++)
            {
                System.out.printf("%5f2 ", p.getData()[i][j]);
            }
            System.out.println();
        }
    }

    private static void print2dMatrix(RealMatrix p) {
        for(int i = 0; i < p.getRowDimension(); i++)
        {
            for(int j = 0; j < p.getColumnDimension(); j++)
            {
                System.out.printf("%5f2 ", p.getData()[i][j]);
            }
            System.out.println();
        }
    }

    private static void checkIfWorking(int iterations) {
        int r2, n = iterations;
        System.out.println("n = " + n);
        double r;
        double[] x = new double[n];
        double[] u = new double[n];
        x[0] = 2;
        for (int i = 0; i<n-1; i++) {
            r2 = ThreadLocalRandom.current().nextInt(3,6);
            u[i] = r2;
            x[i+1] = 0.1*x[i] + 0.3*u[i];
        }
        for (int i = 0; i<n-1; i++) {
            r = ThreadLocalRandom.current().nextDouble(0.1,0.2); //noise
            x[i] += r;
        }
        u[n-1] = 4;
        double[] theta = compute(x,u);
        for(int i = 0; i < theta.length; i++) {
            System.out.println(theta[i]);
        }
        double a  = theta[0];
        double b = theta[1];
        double[] x_hat = new double[n];
        x_hat[0] = x[0];
        for (int i = 0; i<n-1; i++) {
            x_hat[i+1] = a*x_hat[i] + b*u[i];
        }
        System.out.println(Arrays.toString(x));
        System.out.println(Arrays.toString(x_hat));
        System.out.println("BFR: " + bfr(x, x_hat) + "%");
    }

    private static double computebfr (double[] x1, double[] x2, double[] u1, double[] u2) {
        double[] x2_hat = new double[x2.length];
        x2_hat[0] = x2[0];

        System.out.println(Arrays.toString(x1));

        double[] theta = compute(x1, u1);
        System.out.println(Arrays.toString(theta));

        final XYSeries x = new XYSeries("X");
        final XYSeries x_hat = new XYSeries("X_HAT");

        //compute x2_hat
        for (int i = 0; i<x2.length-1; i++) {
            x_hat.add(i, x2_hat[i]);
            x2_hat[i+1] = theta[0]*x2_hat[i] + theta[1]*u2[i];
        }
        x_hat.add(x2.length-1, x2_hat[x2.length-1]);

        for (int k = 0; k < x2.length; k++) {
//            if (x2[k] > 4) {
//                x2[k] = 4;
//            }
            x.add(k, x2[k]);
        }

        final XYSeries[] X = new XYSeries[2];
        X[0] = x;
        X[1] = x_hat;
        createMixedPlot(X, "Xs", "Seconds");
        System.out.println(Arrays.toString(x2_hat));
        System.out.println(Arrays.toString(x2));
        return bfr(x2, x2_hat);
    }

    private static double computebfr (double[] x1, double[] x2, double[][] u1, double[][] u2) {
        int inputs = u2.length;
        double[] x2_hat = new double[x2.length];
        x2_hat[0] = x2[0];

        System.out.println("X1  = " +Arrays.toString(x1));

        double[] theta = compute(x1, u1);
        System.out.println("TH  = " +Arrays.toString(theta));

        final XYSeries x = new XYSeries("X");
        final XYSeries x_hat = new XYSeries("X_HAT");

        //compute x2_hat
        for (int i = 0; i<x2.length-1; i++) {
            x_hat.add(i, x2_hat[i]);
            x2_hat[i+1] = theta[0]*x2_hat[i];
            for (int j = 0; j<inputs; j++) {
                x2_hat[i+1] += theta[j+1]*u2[j][i];
            }
            x2_hat[i+1] = Math.round(x2_hat[i+1]*100.0)/100.0;
        }
        x_hat.add(x2.length-1, x2_hat[x2.length-1]);

        for (int k = 0; k < x2.length; k++) {
//            if (x2[k] > 3.5) {
//                x2[k] = 3.5;
//            }
//            if (x2[k] < 2.5) {
//                x2[k] = 2.5;
//            }
            x.add(k, x2[k]);
        }
        final XYSeries[] X = new XYSeries[2];
        X[0] = x;
        X[1] = x_hat;
        createMixedPlot(X, "Xs", "Seconds");
        System.out.println("X2^ = " + Arrays.toString(x2_hat));
        System.out.println("X2  = " + Arrays.toString(x2));
        return bfr(x2, x2_hat);
    }

    private static double bfr (double[] x, double[] x_hat) {
        RealMatrix rx = new Array2DRowRealMatrix(x);
        RealMatrix rx_hat = new Array2DRowRealMatrix(x_hat);
        double result, num, den;

        num = arrayMean(arrayEachSquared(rx.subtract(rx_hat).getColumn(0)));
        den = arrayMean(arrayEachSquared(arrayEachSubtract(x, arrayMean(x))));

        System.out.println("Num " + Math.round(num*1000.0)/1000.0);
        System.out.println("Den " + Math.round(den*1000.0)/1000.0);

        result = 1 - Math.sqrt(num/den);

        System.out.println("Result " + Math.round(result*1000.0)/1000.0);
        if (result > 0) {
            return result*100;
        }
        else {
            return 0;
        }
    }

    private static double arrayMean (double[] input) {
        double sum = 0;

        for (double value : input) {
            sum += value;
        }

        return sum/input.length;
    }

    private static double[] arrayEachSquared (double[] input) {
        double[] result = new double[input.length];

        for (int i = 0; i < input.length; i++){
            result[i] = input[i]*input[i];
        }

        return result;
    }

    private static double[] arrayEachSubtract(double[] input, double value) {
        double[] result = new double[input.length];

        for (int i = 0; i < input.length; i++){
            result[i] = input[i]-value;
        }

        return result;
    }

    private static void createMixedPlot(XYSeries[] vars, String title, String yAxis) {
        //create mixed plot
        final XYSeriesCollection collection = new XYSeriesCollection();
        for (XYSeries var : vars) {
            collection.addSeries(var);
        }
        Plotter demo = new Plotter(title,collection, yAxis);
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }

}
