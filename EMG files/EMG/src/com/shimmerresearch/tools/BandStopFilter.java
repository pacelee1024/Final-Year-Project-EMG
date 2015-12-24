package com.shimmerresearch.tools;

/**
 * Second Order Butterworth Notch Filter
 * Cutoff Frequencies 50Hz, 60Hz for mains interference. 
 *
 */
public class BandStopFilter {
	double gain;
	double x[] = new double[5];
	double y[] = new double[5];
    double a, b, c, d, e, f;
    double samplingRate;
    double cornerFrequency1;
    double cornerFrequency2; 
    
    public BandStopFilter(double samplingrate, double cornerfrequency1 , double cornerfrequency2)
    {
    	x[0]=0; x[1]=0; x[2]=0; x[3]=0; x[4]=0;
    	y[0]=0; y[1]=0; y[2]=0; y[3]=0; y[4]=0;
        this.samplingRate = samplingrate;
        this.cornerFrequency1 = cornerfrequency1;
        this.cornerFrequency2 = cornerfrequency2;

        if (samplingRate > 249 && samplingRate < 257  && cornerFrequency1 == 49  && cornerFrequency2 == 51) {
        	gain = 1.035319629e+00;
            a = 1.3479653955;
            b = 2.4542526769;
            c = -0.9329347318;
            d = 1.2793795314;
            e = -2.3693624057;
            f = 1.3245803618;
        } else if (samplingRate == 512  && cornerFrequency1 == 49  && cornerFrequency2 == 51)  {
        	gain = 1.017506496e+00;
            a = 3.2705855217;
            b = 4.6741824137;
            c = -0.9658854606;
            d = 3.1864205141;
            e = -4.5934656978;
            f = 3.2422077734;
        } else if (samplingRate == 1024  && cornerFrequency1 == 49  && cornerFrequency2 == 51) {
        	gain = 1.008715265e+00;
            a = 3.8132959456;
            b = 5.6353064922;
            c = -0.9827947193;
            d = 3.7639469933;
            e = -5.5865429879;
            f = 3.7967514068;
        } else if (samplingRate > 249 && samplingRate < 257  && cornerFrequency1 == 59  && cornerFrequency2 == 61) { 
        	gain = 1.035319629e+00;
            a = 0.3921866806;
            b = 2.0384525981;
            c = -0.9329347318;
            d = 0.3722318194;
            e = -1.9677472261;
            f = 0.3853828719;
        } else if (samplingRate == 512  && cornerFrequency1 == 59  && cornerFrequency2 == 61) { 
        	gain = 1.017506496e+00;
            a = 2.9640276873;
            b = 4.1963650327;
            c = -0.9658854606;
            d = 2.8877516164;
            e = -4.1238693043;
            f = 2.9383098361;
        } else if (samplingRate == 1024   && cornerFrequency1 == 59  && cornerFrequency2 == 61) {
            gain = 1.008715265e+00;
            a = 3.7320414500;
            b = 5.4820333461;
            c = -0.9827947193;
            d = 3.6837440352;
            e = -5.4345941164;
            f = 3.7158494456;
        } else {
            this.samplingRate = -1;
            this.cornerFrequency1 = -1;
            this.cornerFrequency2 = -1;
        }
    }

    public double filterData(double data)
    {
        if (samplingRate != -1 && cornerFrequency1 != -1 && cornerFrequency2 != -1)
        {
        	x[0]=x[1]; x[1]=x[2]; x[2]=x[3]; x[3]=x[4];
        	x[4]=data/gain;
        	y[0]=y[1]; y[1]=y[2]; y[2]=y[3]; y[3]=y[4];
        	y[4] = x[0] + x[4] -a*(x[1] + x[3]) + b*x[2] + c*y[0] + d*y[1] + e*y[2] + f*y[3];
        	return y[4];
        }
        else
        {
            return data;
        }
        
    }
	
}
