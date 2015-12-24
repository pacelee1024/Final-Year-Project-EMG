package shimmerSpecific;
import java.util.Collection;
import java.util.Observable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.pcdriver.CallbackObject;
import com.shimmerresearch.pcdriver.ShimmerPC;
import com.shimmerresearch.tools.HighPassFilter;
import com.shimmerresearch.tools.BandStopFilter;

import control.Controller;
import data.Config;
import data.Data;

public class Shimmer extends Observable {

	ShimmerPC mShimmer;

	// Buffer where data will be kept before being read by controller
	public Queue<Data> chan1Buffer = new ConcurrentLinkedQueue<Data>();
	public Queue<Data> chan2Buffer = new ConcurrentLinkedQueue<Data>();
	
	// Name used to identify the device.
	private String devName = "";

	// Status variables.
	private boolean connected = false;
	private boolean streaming = false;

	private long startTime = (System.currentTimeMillis());

	// Filter objects
	private HighPassFilter hpfexg1ch1;
	private HighPassFilter hpfexg1ch2;
	private BandStopFilter bsfexg1ch1;
	private BandStopFilter bsfexg1ch2;

	private double exg1Ch1Data=0, exg1Ch2Data=0;

	private String comPort = "";
	
	private Controller control;
	
	public Shimmer(String name, String COMM, Controller o)
	{
		this.addObserver(o);
		this.comPort = COMM;
		
		this.control = o;
		
		this.devName = name;
		
		// Construct Shimmer object with required config
		/*this.mShimmer = new ShimmerPC(
				this.devName, Config.samplingRate, Config.accelRange, Config.gsrRange, Config.setEnabledSensors,
				Config.continousSync, Config.enableLowPowerAccel, Config.enableLowPowerGyro, 
				Config.enableLowPowerMag, Config.gyroRange, Config.magRange, Config.exg1, Config.exg2
				);*/
		
		this.mShimmer = new ShimmerPC(this.devName, Config.continousSync);


		// Create filters with correct properties
		hpfexg1ch1 = new HighPassFilter(1024, Config.HPF);
		hpfexg1ch2 = new HighPassFilter(1024, Config.HPF);
		bsfexg1ch1 = new BandStopFilter(1024, Config.BSF1, Config.BSF2);
		bsfexg1ch2 = new BandStopFilter(1024, Config.BSF1, Config.BSF2);
	}

	/*
	 *  Set up connection with Shimmer device at specific port
	 */
	public void connect()
	{	
		//Connect to port and make sure it is set up for EMG
		this.mShimmer.connect(this.comPort, "");		
		//this.mShimmer.enableDefaultEMGConfiguration();

		// Wait for responses from device
		@SuppressWarnings("unused")
		WaitForData waitForData = new WaitForData(mShimmer);
	}

	public void disconnect(){
		this.mShimmer.disconnect();
	}
	
	public void startStreaming()
	{
		this.mShimmer.startStreaming();	
		this.startTime = (System.currentTimeMillis());
	}
	
	public void stopStreaming(){
		this.mShimmer.stopStreaming();
	}

	class WaitForData implements com.shimmerresearch.pcdriver.Callable  
	{   
		public WaitForData(ShimmerPC shimmer)  
		{  
			shimmer.passCallback(this);
		}  
		public void callBackMethod(int ind, Object objectCluster)  
		{  
			if (ind == ShimmerPC.MSG_IDENTIFIER_STATE_CHANGE) {
				CallbackObject callbackObject = (CallbackObject)objectCluster;
				int state = callbackObject.mIndicator;

				if (state == ShimmerBluetooth.STATE_CONNECTING) {	//Never called
					System.out.println("Shimmer Connecting");
				} else if (state == ShimmerBluetooth.STATE_CONNECTED) {
					System.out.println("Shimmer Connected");
					setConnected(true);	
					
				} else {
					System.out.println("Shimmer Disconnected");
					setConnected(false);
				}
			}
			else if (ind == ShimmerPC.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
				CallbackObject callbackObject = (CallbackObject)objectCluster;
				int msg = callbackObject.mIndicator;
				if (msg == ShimmerPC.NOTIFICATION_STOP_STREAMING) {
					setStreaming(false);
				} else if (msg == ShimmerPC.NOTIFICATION_START_STREAMING) {
					setStreaming(true);
				} else {	//Ready for Streaming
					System.out.println("Ready to Stream");
				}
			}
			else if (ind == ShimmerPC.MSG_IDENTIFIER_DATA_PACKET) {
				ObjectCluster objc = (ObjectCluster)objectCluster;
				String[] exgnames = {"EMG CH1","EMG CH2", "Timestamp"};
				
				double timeStamp = 0;
				//startTime = (System.currentTimeMillis());
				
				for (int i=0;i<exgnames.length;i++){
					Collection<FormatCluster> cf = objc.mPropertyCluster.get(exgnames[i]);
					
					if (cf.size()!=0){
						double data =((FormatCluster)ObjectCluster.returnFormatCluster(cf,"CAL")).mData;
						
						if (exgnames[i].equals("EMG CH1")) {
							if (Config.HP_EN){
								data = hpfexg1ch1.filterData(data); 
							}
							if (Config.BS_EN){
								data = bsfexg1ch1.filterData(data);
							}
							((FormatCluster)ObjectCluster.returnFormatCluster(cf,"CAL")).mData = data;
							exg1Ch1Data=data;
						} else if (exgnames[i].equals("EMG CH2")) {
							if (Config.HP_EN){
								data = hpfexg1ch2.filterData(data);
							}
							if (Config.BS_EN){
								data = bsfexg1ch2.filterData(data);
							}
							((FormatCluster)ObjectCluster.returnFormatCluster(cf,"CAL")).mData = data;
							exg1Ch2Data=data;
						}	else if (exgnames[i].equals("Timestamp")) {
								timeStamp =((FormatCluster)ObjectCluster.returnFormatCluster(cf,"CAL")).mData; 
						}
					}
				}	

				long time = startTime + (long)timeStamp;

				
				// Add data from both channels into the buffer
				chan1Buffer.add(new Data(time,devName + "C1",exg1Ch1Data));
				chan2Buffer.add(new Data(time,devName + "C2",exg1Ch2Data));
				
				// Notify controller of data being added to buffer
				setChanged();
				notifyObservers();
			}
		}
	}

	private void setConnected(boolean connected) {
		this.connected = connected;
	}

	public boolean isConnected() {
		return this.connected;
	}

	public boolean isStreaming() {
		return this.streaming;
	}

	private void setStreaming(boolean streaming) {
		this.streaming = streaming;
	}

	public String getDevName() {
		return this.devName;
	}


	@Override
	public String toString(){
		return this.devName;
	}
	
	public String getPort(){
		return this.comPort;
	}
	
	public void setPort(String port){
		this.comPort = port;
	}

}
