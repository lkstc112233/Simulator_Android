package com.photoncat.architecturesimulator.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.photoncat.architecturesimulator.FrontPanelActivity;
import com.photoncat.architecturesimulator.simulator.chips.Chip;
import com.photoncat.architecturesimulator.simulator.chips.ChipFactory;
import com.photoncat.architecturesimulator.simulator.chips.ClockRegister;
import com.photoncat.architecturesimulator.simulator.chips.IODevices;
import com.photoncat.architecturesimulator.simulator.chips.Memory;
import com.photoncat.architecturesimulator.simulator.chips.RegisterFile;
import com.photoncat.architecturesimulator.simulator.tools.AssemblyCompiler;
import com.photoncat.architecturesimulator.simulator.tools.AssemblyCompiler.CompiledProgram;
import com.photoncat.architecturesimulator.simulator.util.ConvenientStreamTokenizer;
import static com.photoncat.architecturesimulator.simulator.util.ExceptionHandling.panic;

/**
 * A simulated machine.
 * @author Xu Ke
 *
 */
public class Machine {
	private Keyboard keyboard;
	private Printer printer;
	private CardReader reader;
	public Machine() {
		try {
			loadFile();
		} catch (IOException e) {
			System.err.println("Configuration file not found.");
			System.exit(-1);
		} catch (IllegalStateException e) {
			System.err.println("Configuration file format error:");
			System.err.println(e.getMessage());
			System.exit(-1);
		}
		keyboard = new Keyboard();
		((IODevices)getChip("IO")).connectDevice(0, keyboard);
		printer = new Printer();
		((IODevices)getChip("IO")).connectDevice(1, printer);
		reader = new CardReader();
		((IODevices)getChip("IO")).connectDevice(2, reader);
		((RegisterFile)getChip("IRF")).setValue(0, 0);
		
	}
	/**
	 * Load a testing program by MAGIC!
	 */ 
	public void IPLMagic() {
		Memory mem = (Memory) getChip("memory");
		
		// SIMULATED Boot Loader: 
		// It loads a testing program into the memory address 0x10, and sets PC to
		// 0x10.
		((ClockRegister)getChip("PC")).setValue(1025);
		CompiledProgram code = null;
		try {
			code = AssemblyCompiler.compile(new BufferedReader(new InputStreamReader(FrontPanelActivity.getActivity().getAssets().open("conf/IPL Program.prg"))));
			mem.loadProgram(1025, code);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Loads a configuration file from disk.
	 * Utilizes {@link java.io.StreamTokenizer} to tokenize.
	 * @throws IOException When load file failed.
	 */
	private void loadFile() throws IOException {
		ConvenientStreamTokenizer tokens = new ConvenientStreamTokenizer(new BufferedReader(new InputStreamReader(FrontPanelActivity.getActivity().getAssets().open("conf/chipsDef.ini"))));
		parseChipsDefinition(tokens);
		parseCablesDefinition(tokens);
	}
	/**
	 * Parser for chips definition.
	 * @param tokens
	 * @throws IOException
	 */
	private void parseChipsDefinition(ConvenientStreamTokenizer tokens) throws IOException{
		if (tokens.nextToken() != '{')
			panic("Cannot find openning brackets for chips.");
		while (parseChip(tokens));
		if (tokens.nextToken() != '}')
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be '}'.");
	}
	private boolean parseChip(ConvenientStreamTokenizer tokens) throws IOException{
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_WORD){
			tokens.pushBack();
			return false;
		}
		String chipName = tokens.sval;	
		if (tokens.nextToken() != ':')
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be '.'.");
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_WORD)
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be typeName.");
		String chipType = tokens.sval;
		List<Object> params = new ArrayList<Object>();
		Object param = null;
		while ((param = parseParam(tokens)) != null)
			params.add(param);
		// Make chip.
		chips.put(chipName, ChipFactory.makeChip(chipType, params.toArray()));
		return true;
	}
	private Object parseParam(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ','){
			tokens.pushBack();
			return null;
		}
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be param.");
		return (int)tokens.nval;
	}
	/**
	 * Parser for chips definition.
	 * @param tokens
	 * @throws IOException
	 */
	private void parseCablesDefinition(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != '{')
			panic("Cannot find openning brackets for cables.");
		while (parseCable(tokens));
		if (tokens.nextToken() != '}')
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be '}'.");
	}
	private boolean parseCable(ConvenientStreamTokenizer tokens) throws IOException {
		Object[] chipPortDef = parseChipPort(tokens);
		if (chipPortDef == null)
			return false;
		if (chipPortDef.length != 2) {
			// handle partial in first element.
		}
		Cable workingCable = new SingleCable(getChip((String)chipPortDef[0]).getPortWidth((String)chipPortDef[1]));
		getChip((String)chipPortDef[0]).connectPort((String)chipPortDef[1], workingCable);
		int token = tokens.nextToken();
		while (token == '-') {
			chipPortDef = parseChipPort(tokens);
			if (chipPortDef == null)
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nExpecting: Chip.Port");
			// check if it's a definition containing [.
			if (chipPortDef.length == 2) {
				if (getChip((String)chipPortDef[0]).getPortWidth((String)chipPortDef[1]) == workingCable.getWidth())
					getChip((String)chipPortDef[0]).connectPort((String)chipPortDef[1], workingCable);
				else {
					Cable adapter = new CableAdapter(getChip((String)chipPortDef[0]).getPortWidth((String)chipPortDef[1]), workingCable);
					getChip((String)chipPortDef[0]).connectPort((String)chipPortDef[1], adapter);
				}
			} else {
				int width = ((Integer) chipPortDef[3]) - ((Integer) chipPortDef[2]) + 1;
				Cable adapter;
				if (chipPortDef.length < 5)
					adapter = new CablePartialAdapter(width, workingCable);
				else
					adapter = new CablePartialAdapter(width, workingCable, (Integer)chipPortDef[4]);
				if (width != workingCable.getWidth()) {
					// Need a new adapter for chip.
					Cable adapter2 = new CablePartialAdapter(getChip((String)chipPortDef[0]).getPortWidth((String)chipPortDef[1]), adapter, - ((Integer) chipPortDef[2]));
					getChip((String)chipPortDef[0]).connectPort((String)chipPortDef[1], adapter2);
				} else {
					getChip((String)chipPortDef[0]).connectPort((String)chipPortDef[1], adapter);
				}
			}
			token = tokens.nextToken();
		}
		if (token == ':') {
			if (tokens.nextToken() != ConvenientStreamTokenizer.TT_WORD)
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nExpecting: Cable name.");
			cables.put(tokens.sval, workingCable);
			return true;
		}
		tokens.pushBack();
		return true;
	}
	
	private Object[] parseChipPort(ConvenientStreamTokenizer tokens) throws IOException{
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_WORD) {
			tokens.pushBack();
			return null;
		}
		String chipName = tokens.sval;
		if (tokens.nextToken() != '.')
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be '.'.");
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_WORD)
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be port name.");
		String portName = tokens.sval;
		if (tokens.nextToken() != '[') {
			tokens.pushBack();
			return new Object[]{chipName,portName};
		}
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be pinStart.");
		int startingPort = (int) tokens.nval;
		int token = tokens.nextToken();
		int endingPort = -1;
		switch(token){
		case ':':
			if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be pinEnd.");
			endingPort = (int) tokens.nval;
			break;
		case ',':
			if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be offset.");
			endingPort = (int) tokens.nval;
			if (tokens.nextToken() != ']')
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be ']'.");
			return new Object[]{chipName, portName, startingPort, startingPort, endingPort};
		case ']':
			return new Object[]{chipName, portName, startingPort, startingPort};
		default:
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be ':' or ']' or ','.");
		}
		token = tokens.nextToken();
		int offset = -1;
		switch(token){
		case ',':
			if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be offset.");
			offset = (int) tokens.nval;
			if (tokens.nextToken() != ']')
				panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be ']'.");
			return new Object[]{chipName, portName, startingPort, endingPort, offset};
		case ']':
			return new Object[]{chipName, portName, startingPort, endingPort};
		default:
			panic("Unexpected token: \n\t" + (tokens.ttype > 0 ? ((char)tokens.ttype) : tokens.sval) + "\n\tat line " + tokens.lineno()+"\nShould be ':' or ']' or ','.");
		}
		return null; // Won't reach here.
	}
	
	private Map<String, Chip> chips = new HashMap<>();
	private Map<String, Cable> cables = new HashMap<>();
	public Chip getChip(String name) {
		return chips.get(name);
	}
	public Cable getCable(String name) {
		return cables.get(name);
	}
	/**
	 * Tick each chip.
	 */
	public void tick(){
		for (Map.Entry<String, Chip> e : chips.entrySet()) {
			e.getValue().tick();
		}
	}
	/**
	 * Evaluates until all values are stabilized.
	 */
	public void evaluate(){
		boolean change = true;
		while (change) {
			change = false;
			for (Map.Entry<String, Chip> e : chips.entrySet()) {
				if (e.getValue().evaluate())
					change = true;
			}
		}
	}
	public String getScreen(){
		return printer.toString();
	}
	public void keyPress(short key) {
		keyboard.pressKey(key);
	}
	public void insertCard(InputStream card) {
		reader.insertCard(card);
	}
}
