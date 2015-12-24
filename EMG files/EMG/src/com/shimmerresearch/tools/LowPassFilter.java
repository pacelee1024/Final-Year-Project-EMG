package com.shimmerresearch.tools;

/*
 * Copyright (c) 2014, Shimmer Research, Ltd.
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:

 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Shimmer Research, Ltd. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * @author Cathy Swanton , Jong Chern Lim
 * @date   March, 2014
 */

//this is a second order low pass butterworth filter 
//http://www-users.cs.york.ac.uk/~fisher/mkfilter/trad.html

public class LowPassFilter
{
	private double GAIN;
	private double xv0, xv1, xv2;
	private double yv0, yv1, yv2;
	private double a, b;
	private double samplingrate;
	private double cornerfrequency;

	public LowPassFilter(double samplingrate, double cornerfrequency)
	{
		xv0 = 0;
		xv1 = 0;
		xv2 = 0;
		yv0 = 0;
		yv1 = 0;
		yv2 = 0;
		this.samplingrate = samplingrate;
		this.cornerfrequency = cornerfrequency;

		/////
		if (samplingrate == 51.2 && cornerfrequency == 5)
		{
			GAIN = 1.542807812e+01;
			a = -0.4213046261;
			b = 1.1620370772;
		}
		else if (samplingrate == 102.4 && cornerfrequency == 5)
		{
			GAIN = 5.197890536e+01;
			a = -0.6480567349;
			b = 1.5711024402;
		}
		else if (samplingrate == 128 && cornerfrequency == 5)
		{
			GAIN = 7.820233128e+01;
			a = -0.7067570632;
			b = 1.6556076929;
		}
		else if (samplingrate == 204.8 && cornerfrequency == 5)
		{
			GAIN = 1.887247719e+02;
			a = -0.8049825944;
			b = 1.7837877086;
		}
		else if (samplingrate == 256 && cornerfrequency == 5)
		{
			GAIN = 2.889601535e+02;
			a = -0.8406758501;
			b = 1.8268331110;
		}
		else if (samplingrate == 512 && cornerfrequency == 5)
		{
			GAIN = 1.108844743e+03;
			a = -0.9168833468;
			b = 1.9132759887;
		}
		else if (samplingrate == 1024 && cornerfrequency == 5)
		{
			GAIN = 4.342236967e+03;
			a = -0.9575402448;
			b = 1.9566190606;
		}
		else
		{
			samplingrate = -1;
			cornerfrequency = -1;
		}
	}

	public double filterData(double data)
	{
		if (samplingrate != -1 && cornerfrequency != -1)
		{
			xv0 = xv1; xv1 = xv2;
			xv2 = data / GAIN;
			yv0 = yv1; yv1 = yv2;
			yv2 = (xv0 + xv2) + 2 * xv1 + (a * yv0) + (b * yv1);
			return yv2;
		}
		else
		{
			return data;
		}


	}


}

