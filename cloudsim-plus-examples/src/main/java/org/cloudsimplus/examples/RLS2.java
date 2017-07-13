package org.cloudsimplus.examples;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.cloudbus.cloudsim.core.Simulation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RefineryUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by avgr_m on 05/07/2017.
 */
public class RLS2 {

    //Check for Test Commit #1
    //Check for Test Commit #2

    public static void main(String[] args) {
//        matrixExample();
//        checkIfWorkingDecoupled(3000);
//        checkIfWorkingCoupled(10000);
//        double data[][] = CSVUtils.readTo2dArray("/Users/avgr_m/Desktop/CSVs/Vm0@2017.07.11.13.05.28.csv", true);
//        RealMatrix rm = new Array2DRowRealMatrix(data);
//        System.out.println(Arrays.toString(rm.getColumn(1))); //art
//        System.out.println(Arrays.toString(rm.getColumn(2))); //cpuutil
//        double[] theta = compute(rm.getColumn(1), rm.getColumn(2));
//        System.out.println(Arrays.toString(theta));

        double data1[][] = CSVUtils.readTo2dArray("/Users/avgr_m/Desktop/CSVs/Vm0@2017.07.12.17.16.24.csv", true);
        double data2[][] = CSVUtils.readTo2dArray("/Users/avgr_m/Desktop/CSVs/Vm1@2017.07.12.17.16.24.csv", true);
        RealMatrix rm = new Array2DRowRealMatrix(data1);
        RealMatrix rm2 = new Array2DRowRealMatrix(data2);
        //double[] x = rm.getColumn(1);
        double[][] x = {rm.getColumn(1),rm2.getColumn(1)};
        double[][] u = {rm.getColumn(3),rm2.getColumn(3)};
//        double[][] u = {rm.getColumn(3),rm.getColumn(6)};
//        double[] u = rm.getColumn(3);
        //System.out.println("BFR: " + computebfr(x, rm2.getColumn(1), u) + "%");
       // System.out.println(Arrays.toString(compute(x, u)));
//        checkIfWorkingDecoupled(10000);
    }

    public static double[] compute(double[] x, double[] u) {
        int inputs = 1;
        int samples = x.length;

        double[][] temp_f = {x,u};
        RealMatrix f = new Array2DRowRealMatrix(temp_f);

        return doTheRLS(samples, inputs, 1, f, 0);
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

        return doTheRLS(samples, inputs, 1, f, 0);
    }

    public static double[] compute(double[][] x, double[][] u, int whichState) {
        int inputs = u.length;
        int states = x.length;
        int samples = x[0].length;

//        System.out.println("Inputs = " + inputs);
        // init f
        System.out.println("Xdim = " + x.length);
        System.out.println("Xdim = " + x[0].length);
        System.out.println("Udim = " + u.length);
        System.out.println("Udim = " + u[0].length);
        RealMatrix f = new Array2DRowRealMatrix(new double[inputs+states][samples]);
//        System.out.println("Rows = " + f.getRowDimension());
        RealMatrix rmu1 = new Array2DRowRealMatrix(u,true);
        RealMatrix rmx1 = new Array2DRowRealMatrix(x, true);

        //rmx = rmx.transpose();
//        print2dMatrix(rmx);
//        print2dMatrix(rmu);

//        f.setRowVector(0, rmx.getRowVector(0));
        for (int r = 0; r < states; r++) {
            f.setRow(r, rmx1.getRow(r));
        }
        for (int r = states; r < inputs+states; r++) {
            f.setRow(r, rmu1.getRow(r-states));
        }
        //print2dMatrix(f);

        return doTheRLS(samples, inputs, states, f, whichState);
    }

    public static double[] doTheRLS(int samples, int inputs, int states, RealMatrix f, int whichState) {
        RealMatrix f_col, identM, ffp, fpf, pfe, thNewMatr;

        //init plot arrays
//        final XYSeries alpha = new XYSeries("a");
//        final XYSeries beta = new XYSeries("b");
//        final XYSeries gamma = new XYSeries("c");

        final XYSeries[] vars = new XYSeries[inputs+states];
        for (int q = 0; q < inputs+states; q++) {
            vars[q] = new XYSeries(q);
        }

        //init error
        double e;

        // init theta
        double[][] temp_th = new double[inputs+states][samples];
        for (double[] row: temp_th)
            Arrays.fill(row, 0.0);
        for (int k = 0; k < inputs+states; k++) {
            temp_th[k][0] = ThreadLocalRandom.current().nextDouble(0, 1); // uniform
        }
//        temp_th[1][0] = ThreadLocalRandom.current().nextDouble(-0.7,-0.3);
        RealMatrix th = new Array2DRowRealMatrix(temp_th);

        // init p
        RealMatrix p = MatrixUtils.createRealIdentityMatrix(inputs+states);
        p = p.scalarMultiply(10000);

        for (int i = 1; i < samples; i++) {
            f_col = f.getColumnMatrix(i-1);
            e = f.getEntry(whichState,i) - (f_col.transpose().multiply(th.getColumnMatrix(i-1))).getEntry(0,0);
            identM = MatrixUtils.createRealIdentityMatrix(inputs+states);
            ffp = f_col.multiply(f_col.transpose()).multiply(p);
            fpf = f_col.transpose().multiply(p).multiply(f_col);
            p = p.multiply(identM.subtract(ffp.scalarMultiply(1/(1 + fpf.getEntry(0,0)))));
            pfe = p.multiply(f_col).scalarMultiply(e);
            thNewMatr = th.getColumnMatrix(i-1).add(pfe);
            th.setColumnMatrix(i, thNewMatr);
            for (int q = 0; q < inputs+states; q++) {
                vars[q].add(i,th.getEntry(q,i));
            }
            //System.out.println(th.getEntry(1,i));
        }

        createMixedPlot(vars, "Theta", "Value");
        return th.getColumn(samples-1);
    }

    private static double computebfr (double[] x1, double[] x2, double[] u) {
        double[] x2_hat = new double[x2.length];
        x2_hat[0] = x2[0];

        System.out.println(Arrays.toString(x1));

        double[] theta = compute(x1, u);
        System.out.println(Arrays.toString(theta));

        final XYSeries x = new XYSeries("X");
        final XYSeries x_hat = new XYSeries("X_HAT");

        //compute x2_hat
        for (int i = 0; i<x2.length-1; i++) {
            x_hat.add(i, x2_hat[i]);
            x2_hat[i+1] = theta[0]*x2_hat[i] + theta[1]*u[i];
        }
        x_hat.add(x2.length-1, x2_hat[x2.length-1]);

        for (int k = 0; k < x2.length; k++) {
            if (x2[k] > 4) {
                x2[k] = 4;
            }
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

    private static double computebfr (double[] x1, double[] x2, double[][] u) {
        int inputs = u.length;
        double[] x2_hat = new double[x2.length];
        x2_hat[0] = x2[0];

        System.out.println("X1  = " +Arrays.toString(x1));

        double[] theta = compute(x1, u);
        System.out.println("TH  = " +Arrays.toString(theta));

        final XYSeries x = new XYSeries("X");
        final XYSeries x_hat = new XYSeries("X_HAT");

        //compute x2_hat
        for (int i = 0; i<x2.length-1; i++) {
            x_hat.add(i, x2_hat[i]);
            x2_hat[i+1] = theta[0]*x2_hat[i];
            for (int j = 0; j<inputs; j++) {
                x2_hat[i+1] += theta[j+1]*u[j][i];
            }
            x2_hat[i+1] = Math.round(x2_hat[i+1]*100.0)/100.0;
        }
        x_hat.add(x2.length-1, x2_hat[x2.length-1]);

        for (int k = 0; k < x2.length; k++) {
//            if (x2[k] > 4) {
//                x2[k] = 4;
//            }
//            if (x2[k] < 2.7) {
//                x2[k] = 3;
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

    ///////////////////////////////////////////////////////////////// Examples
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

    private static void checkIfWorkingDecoupled(int iterations) {
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

    private static void checkIfWorkingCoupled(int iterations) {
        int n = iterations;
        System.out.println("n = " + n);
        double r, r1, r2;
        double[][] x = new double[2][n];
        double[][] u = new double[2][n];
        x[0][0] = 2;
        x[1][0] = 2;
        //simulate results
        for (int i = 0; i<n-1; i++) {
            r1 = ThreadLocalRandom.current().nextDouble(0.3,0.9);
            r2 = ThreadLocalRandom.current().nextDouble(0.3,0.9);
            u[0][i] = r1;
            u[1][i] = r1;
            x[0][i+1] = 0.1*x[0][i] + 0.5*x[1][i] - 0.3*u[0][i] + 0.7*u[0][i];
            x[1][i+1] = 0.4*x[0][i] + 0.3*x[1][i] + 0.8*u[0][i] - 0.2*u[0][i];
        }
        //noise
        for (int i = 0; i<n-1; i++) {
            r1 = ThreadLocalRandom.current().nextDouble(0.1,0.2); //noise
            r2 = ThreadLocalRandom.current().nextDouble(0.1,0.2); //noise
            x[0][i] += r1;
            x[1][i] += r2;
        }
        u[0][n-1] = 0.6;
        u[1][n-1] = 0.6;
        double[] theta = compute(x,u, 0);
        for(int i = 0; i < theta.length; i++) {
            System.out.println(theta[i]);
        }
        double a11  = theta[0];
        double a12  = theta[1];
        double b11 = theta[2];
        double b12 = theta[3];
        System.out.println(Arrays.toString(theta));
        theta = compute(x,u, 1);
        double a21  = theta[0];
        double a22  = theta[1];
        double b21 = theta[2];
        double b22 = theta[3];
        System.out.println(Arrays.toString(theta));
        double[][] x_hat = new double[2][n];
        x_hat[0][0] = x[0][0];
        x_hat[1][0] = x[1][0];
        for (int i = 0; i<n-1; i++) {
            x_hat[0][i+1] = a11*x_hat[0][i] + a12*x_hat[1][i] + b11*u[0][i] + b12*u[0][i];
            x_hat[1][i+1] = a21*x_hat[0][i] + a22*x_hat[1][i] + b21*u[0][i] + b22*u[0][i];
        }
        System.out.println(Arrays.toString(x[0]));
        System.out.println(Arrays.toString(x_hat[0]));
        System.out.println("BFR: " + bfr(x[0], x_hat[0]) + "%");
    }

}
