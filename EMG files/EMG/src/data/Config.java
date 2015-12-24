package data;
import com.shimmerresearch.driver.ShimmerObject;

public final class Config {
	
	/*
	 * Filter configuration
	 */
	
	// Set whether filters and enabled
	public static final boolean BS_EN = true;
	public static final boolean HP_EN = true;

	// Corner frequencies for the filters
	public static final double BSF1=49;
	public static final double BSF2=51;
	public static final double HPF=0.5;
	
	/*
	 * Device settings
	 */
	
	public static final String[] DEVICE_NAMES = {"D1", "D2", "D3"};
	public static final int WINDOW_LEN = 200;

	/*
	 * Logging configuration
	 */
	
	public static final boolean LOGGING_EN = true;
	// Filename to save log as
	public static final String FILENAME = "log.csv";
	// Value delimiter used to create csv logging file
	public static final String DELIM = ",";
	// Address of server where GET command should be sent for REST
	public static final String ADDR = "http://192.168.1.140:8080/WbsnServices/webapi/emg/act3";
	// List of all the tags that will be used
	public static final String[] TAGS = {"D1C1Raw","D1C1Calc","D1C2Raw","D1C2Calc","D2C1Raw","D2C1Calc","D2C2Raw",
										"D2C2Calc","D3C1Raw","D3C1Calc","D3C2Raw","D3C2Calc"};
	// Number of samples to send in one request
	public static final int SAMPLES = 40;
	
	// Length of time between sending requests (ms)
	public static final int SENDPERIOD = 200;
	
	/*
	 * Configuration parameters for Shimmer device set up as EMG.
	 * 
	 * WARNING these do not work properly
	 * TODO find error in these
	 */
	
	// Set sampling rate as 1024 Hz
	public static final double samplingRate = 1024;
	// 0 represents default value (+/- 1.5g).
	public static final int accelRange = 0;
	// 0 represents default value (10kOhm to 56kOhm).
	public static final int gsrRange = 0;
	// Only want to enable the EMG sensor and ensure it is 24 bit.
	public static final int setEnabledSensors = ShimmerObject.SENSOR_EMG|ShimmerObject.SENSOR_EXG1_24BIT|ShimmerObject.SENSOR_EXG2_24BIT;
	// Device should update host
	public static final boolean continousSync = true;
	// These do not need to be enabled
	public static final boolean enableLowPowerAccel = false;
	public static final boolean enableLowPowerGyro = false;
	public static final boolean enableLowPowerMag = false;
	// 0 represents default value 
	public static final int gyroRange = 0;
	// 0 represents default value 
	public static final int magRange = 0;
	// Register for EXG chip 1, Default EMG settings
	public static final byte[] exg1 = {04, -96, 16, 105, 96, 32, 00, 00, 02, 03};
	// Register for EXG chip 2, Default EMG settings
	public static final byte[] exg2 = {04, -96, 16, -31, -31, 00, 00, 00, 02, 01};
}
