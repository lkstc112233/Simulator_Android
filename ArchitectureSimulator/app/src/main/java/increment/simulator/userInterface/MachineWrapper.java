package increment.simulator.userInterface;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableInt;

import com.photoncat.architecturesimulator.BR;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import increment.simulator.Machine;
import increment.simulator.chips.BulbSet;
import increment.simulator.chips.Memory;
import increment.simulator.chips.NumberedSwitch;
import increment.simulator.chips.Switch;
import increment.simulator.chips.SwitchesSet;
import increment.simulator.tools.AssemblyCompiler;

/**
 * A wrapper class for machine intended to support features from data binding.
 * @author Xu Ke
 *
 */
public class MachineWrapper extends BaseObservable {
	public final ObservableInt tick = new ObservableInt(0);
    // Define the properties for wrappers.
    public final ObservableArrayList<Boolean> valueBulbs = new ObservableArrayList<>();
    public final ObservableArrayList<Boolean> addressBulbs = new ObservableArrayList<>();
    public final ObservableArrayList<Boolean> switches = new ObservableArrayList<>();

	public MachineWrapper(Machine machine) {
    	this.machine = machine;
        for (int i = 0; i < 16; ++i) {
            valueBulbs.add(false);
            addressBulbs.add(false);
            switches.add(false);
        }
        updateEvent();
    }
	
    private Machine machine;
    
	// Define a getter for the property's value
	@Bindable
    public final String getProgramCounter(){ try{return machine.getChip("PC").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getBus(){ try{return machine.getCable("bus").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getMemoryAddressRegister(){ try{return machine.getChip("MAR").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getMemoryBufferRegister(){ try{return machine.getChip("MBR").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getInstructionRegister(){ try{return machine.getChip("IR").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getGeneralPurposeRegisterFile(){ try{return machine.getChip("GPRF").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getIndexRegisterFile(){ try{return machine.getChip("IRF").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getMemory(){ try{return machine.getChip("memory").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final String getControlUnit(){ try{return machine.getChip("CU").toString();}catch(NullPointerException e){return "Not Found";} }
	@Bindable
    public final Integer getRadioSwitch(){ try{return ((NumberedSwitch) machine.getChip("panelDestSelectSwitch")).getValue();}catch(NullPointerException e){return 0;} }
    public final void setRadioSwitch(Integer value){ try{((NumberedSwitch) machine.getChip("panelDestSelectSwitch")).setValue(value);}catch(NullPointerException e){} }
	@Bindable
    public final Integer getRegisterRadioSwitch(){ try{return ((NumberedSwitch) machine.getChip("panelRegSelSwitch")).getValue();}catch(NullPointerException e){return 0;}  }
	public final void setRegisterRadioSwitch(Integer value) { try{((NumberedSwitch) machine.getChip("panelRegSelSwitch")).setValue(value);}catch(NullPointerException e){} }
	@Bindable
	public final Boolean getPaused(){ try{return machine.getCable("paused").getBit(0);}catch(NullPointerException e){return false;} }
	@Bindable
	public final String getScreen(){ try{return machine.getScreen();}catch(NullPointerException e){return "Not Found";} }
    
	private boolean toTick = true;
	private void updateEvent() {
        notifyPropertyChanged(BR._all);
        for (int i = 0; i < 16; ++i) {
            try{ valueBulbs.set(i, ((BulbSet)machine.getChip("panelValue")).getBit(i)); }catch(NullPointerException e){valueBulbs.set(i, false);}
            try{ addressBulbs.set(i, ((BulbSet)machine.getChip("panelAddress")).getBit(i)); }catch(NullPointerException e){valueBulbs.set(i, false);}
            try{((SwitchesSet)machine.getChip("panelSwitchSet")).flipBit(i, switches.get(i));}catch(NullPointerException e){}
        }
	}
    public void tick() {
    	if (toTick){
    		machine.tick();
			tick.set(tick.get() + 1);
    	} else {
        	machine.evaluate();
    	}
    	toTick = !toTick;
    	updateEvent();
    }
    public void forceTick() {
    	machine.evaluate();
    	machine.tick();
    	toTick = false;
    	tick.set(tick.get() + 1);
    	updateEvent();
    }
    public void forceUpdate() {
    	machine.evaluate();
    	updateEvent();
    }
	public void putProgram(String address, String program) throws IllegalStateException, NumberFormatException{
		int intAddress = Integer.decode(address);
		((Memory)machine.getChip("memory")).loadProgram(intAddress, AssemblyCompiler.compile(program));
    	updateEvent();
	}
	public void resetCUStatus() {
		((Switch) machine.getChip("panelResetCU")).flip(true);
		forceTick();
		((Switch) machine.getChip("panelResetCU")).flip(false);
	}
	public void forceLoadMAR() {
		loadSomething(1);
	}
	public void forceLoad() {
		((Switch) machine.getChip("panelLoadSwitch")).flip(true);
		forceTick();
		((Switch) machine.getChip("panelLoadSwitch")).flip(false);
		forceUpdate();
	}
	private void loadSomething(int id) {
		((Switch) machine.getChip("panelLoadSwitch")).flip(true);
		int oldValue = getRadioSwitch();
		setRadioSwitch(id);
		forceTick();
		((Switch) machine.getChip("panelLoadSwitch")).flip(false);
		setRadioSwitch(oldValue);
		forceUpdate();
	}
	private boolean paused = false;
	public void pauseOrRestore() {
		paused = !paused;
		((Switch) machine.getChip("panelPauseCU")).flip(paused);
		forceUpdate();
	}
	public void IPLButton() {
		machine.IPLMagic();
    	updateEvent();
	}
	
	public void keyPress(short key) {
		machine.keyPress(key);
		forceUpdate();
	}
	
	public void insertCard(File card) {
		try {
			machine.insertCard(new FileInputStream(card));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
