package org.cloudsimplus.examples;

/**
 * Created by avgr_m on 05/07/2017.
 */
public class RLS {

    // npar = the number of parameters to be estimated
    // ncov = the number of parameters in the upper triangular covariance matrix
    // nc = the number of noise parameters; if nc > 0 then recursive maximum likelihood is used; if nc = 0 then normal recursive least squares
    // cov = the upper triangular covariance matrix stored as a long vector
    // th = the vector of unknown parameters
    // dat = the vector of observed data
    // r = the vector of past residuals
    // yt = the new output observation

    public static void compute(double[] dat, double[] th, double[] cov, double[] r, int nc, int npar, double yt, double ff) {
        int j, k;
        double cp;
        double[] covdat, gain;

        //form the current prediction error r[0]
        r[0] = yt;
        for (int i = 1; i < npar+1; i++) {
            r[0] = r[0] - dat[i]*th[i];
        }

        //update prediction error if rml used
        if (nc>0) {
            for (int i = nc; i > 0; i--) {
                r[i] = r[i-1];
            }
        }

        //form current covarience data vector covdat[i]
        cp=1;
        covdat = new double[th.length];
        for (int i = 0; i < npar; i++) {
            covdat[i] = 0;
            for (k = 0; k < npar; k++) {
                if (k < i) {
                    j = (k-1)*(npar-1)-(k-1)*(k-2)/2 + i;
                }
                else {
                    j = (i-1)*(npar-1)-(i-1)*(i-2)/2 + k;
                }
                covdat[i] += cov[j]*dat[k];
            }
            cp += covdat[i]*dat[i];
        }
        gain = new double[th.length];

        //form kalman gain vector gain[i] and update parameter vector th[i]
        for (int i = 0; i < npar; i++) {
            gain[i] = covdat[i]/cp;
            th[i] += gain[i]*r[0];
        }

        //update covariance matrix stored in vector cov[i]
        k=0;
        for (int i = 0; i < npar; i++) {
            for (j = 0; j < npar; j++) {
                if (!(j<i)) {
                    cov[k] -= gain[i]*covdat[j];
                    k++;
                }
            }
        }

        //apply forgetting factor ff to diagonal of covariance matrix
        if (ff<1) {
            k = 0;
            for (int i = 0; i < npar; i++) {
                cov[k] /= ff;
                for (j = i; j < npar; j++) {
                    k++;
                }
            }
        }
    }
}
