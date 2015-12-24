package control;
import java.util.NavigableSet;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentSkipListSet;

import data.Config;
import data.Data;
import data.RMS;
import data.Storage;
import shimmerSpecific.Shimmer;



public class Controller  implements Observer{
	
	public Shimmer[] devices = new Shimmer[Config.DEVICE_NAMES.length];
	
	public NavigableSet<Data> archive = new ConcurrentSkipListSet<Data>();
	
	RMS[] rmsFiltersChan1 = new RMS[Config.DEVICE_NAMES.length];
	RMS[] rmsFiltersChan2 = new RMS[Config.DEVICE_NAMES.length];
	
	private Storage logger;
		
	private boolean start = false;
	
	public Controller(){
		
		logger = new Storage(Config.LOGGING_EN, Config.FILENAME, this);
		
		// Set up separate thread for the logger
		Thread t = (new Thread(logger));
		t.start();
		
		for(int i = 0; i < Config.DEVICE_NAMES.length; i++){
			this.rmsFiltersChan1[i] = new RMS(Config.WINDOW_LEN);
			this.rmsFiltersChan2[i] = new RMS(Config.WINDOW_LEN);
		}
		
		this.devices[0] = new Shimmer(Config.DEVICE_NAMES[0], "COM4", this);
		this.devices[1] = new Shimmer(Config.DEVICE_NAMES[1], "COM5", this);
		this.devices[2] = new Shimmer(Config.DEVICE_NAMES[2], "COM8", this);

	}

	@Override
	public void update(Observable arg0, Object arg1) {
	
		if(arg0.getClass() == this.devices[0].getClass()){
			this.newData((Shimmer)arg0);		
		}

	}
	
	private void newData(Shimmer dev){
		
		// Process Channel 1 buffer
		while(!dev.chan1Buffer.isEmpty()){
			
			Data d1 = dev.chan1Buffer.poll();					
			
			for(int i = 0; i < Config.DEVICE_NAMES.length; i++){
				if(dev.getDevName().equals(Config.DEVICE_NAMES[i])){
					
					double raw = d1.getData(dev.getDevName()+"C1");
					Double calc = this.rmsFiltersChan1[i].doRMS(d1.getData(dev.getDevName()+"C1"));
					
					Data temp = new Data(d1.getTimeStamp(),(dev.getDevName()+"C1Raw"),raw);
					
					if(calc != null){
						// Update both
						
						if(this.archive.contains(temp)){
							
							this.archive.ceiling(temp).addData((dev.getDevName()+"C1Raw"), raw);
							this.archive.ceiling(temp).addData((dev.getDevName()+"C1Calc"), calc);
						}
						else{
							temp.addData((dev.getDevName()+"C1Calc"),calc);
							this.archive.add(temp);
						}
						System.out.println(dev.getDevName()+"C1" + " - " + d1.getTimeStamp() + ":   " + calc);
					}
					else{
						if(this.archive.contains(temp)){
							
							this.archive.ceiling(temp).addData((dev.getDevName()+"C1Raw"), raw);
						}
						else{
							this.archive.add(temp);
						}
						System.out.println(dev.getDevName()+"C1" + " Window not full!");
					}
					break;
				}
			}
			
		}
		
		// Process Channel 2 buffer
		while(!dev.chan2Buffer.isEmpty()){
			
			Data d2 = dev.chan2Buffer.poll();

			for(int i = 0; i < Config.DEVICE_NAMES.length; i++){
				if(dev.getDevName().equals(Config.DEVICE_NAMES[i])){
					
					double raw = d2.getData(dev.getDevName()+"C2");
					Double calc = this.rmsFiltersChan2[i].doRMS(d2.getData(dev.getDevName()+"C2"));
					
					Data temp = new Data(d2.getTimeStamp(),(dev.getDevName()+"C2Raw"),raw);
					
					if(calc != null){
						// Update both
						
						if(this.archive.contains(temp)){
							
							this.archive.ceiling(temp).addData((dev.getDevName()+"C2Raw"), raw);
							this.archive.ceiling(temp).addData((dev.getDevName()+"C2Calc"), calc);
						}
						else{
							temp.addData((dev.getDevName()+"C2Calc"),calc);
							this.archive.add(temp);
						}
						System.out.println(dev.getDevName()+"C2" + " - " + d2.getTimeStamp() + ":   " + calc);
					}
					else{
						System.out.println(dev.getDevName()+"C2" + " Window not full!");
						if(this.archive.contains(temp)){
							
							this.archive.ceiling(d2).addData((dev.getDevName()+"C2Raw"), raw);
						}
						else{
							this.archive.add(temp);
						}
					}
					break;
				}
		}
	}
	}

	public boolean isStart() {
		return start;
	}
}
