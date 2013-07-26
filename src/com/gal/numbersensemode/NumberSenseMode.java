package com.gal.numbersensemode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import processing.app.RunnerListener;
import processing.app.Sketch;
import processing.app.Base;
import processing.app.Editor;
import processing.app.EditorState;
import processing.app.Mode;
import processing.app.SketchCode;
import processing.app.SketchException;
import processing.mode.java.JavaBuild;
import processing.mode.java.JavaMode;
import processing.mode.java.runner.Runner;

/**
 * Mode for enabling real-time modifications to numbers in the code.
 *
 */
public class NumberSenseMode extends JavaMode {
	NumberSenseEditor editor;
	
    public NumberSenseMode(Base base, File folder) {
        super(base, folder);
    }

    /**
     * Return the pretty/printable/menu name for this mode. This is separate
     * from the single word name of the folder that contains this mode. It could
     * even have spaces, though that might result in sheer madness or total
     * mayhem.
     */
    @Override
    public String getTitle() {
        return "Number Sense";
    }

    /**
     * Create a new editor associated with this mode.
     */
    @Override
    public Editor createEditor(Base base, String path, EditorState state) {
    	System.out.println("Mode.createEditor!!!!!!!!!!!!!!!!!!!!");
    	editor = new NumberSenseEditor(base, path, state, this);
    	return (Editor)editor;
    }

    /**
     * Returns the default extension for this editor setup.
     */
    /*
    @Override
    public String getDefaultExtension() {
        return null;
    }
    */

    /**
     * Returns a String[] array of proper extensions.
     */
    /*
    @Override
    public String[] getExtensions() {
        return null;
    }
    */

    /**
     * Get array of file/directory names that needn't be copied during "Save
     * As".
     */
    /*
    @Override
    public String[] getIgnorable() {
        return null;
    }
    */
    
    /**
     * Retrieve the ClassLoader for JavaMode. This is used by Compiler to load
     * ECJ classes. Thanks to Ben Fry.
     *
     * @return the class loader from java mode
     */
    @Override
    public ClassLoader getClassLoader() {
        for (Mode m : base.getModeList()) {
            if (m.getClass() == JavaMode.class) {
                JavaMode jMode = (JavaMode) m;
                return jMode.getClassLoader();
            }
        }
        return null;  // badness
    }

    @Override
    public Runner handleRun(Sketch sketch, RunnerListener listener) throws SketchException {
    	System.out.println("I'm handling run now!!!????");
    	
    	automateSketch(sketch);
    	
        JavaBuild build = new JavaBuild(sketch);
        String appletClassName = build.build(false);
        if (appletClassName != null) {
          final Runner runtime = new Runner(build, listener);
          new Thread(new Runnable() {
            public void run() {
              runtime.launch(false);  // this blocks until finished
            }
          }).start();
      		editor.updateInterface(getAllNumbers(sketch));
      		editor.startInteractiveMode();      	
          return runtime;
        }
        return null;    	
    }
    
    private boolean automateSketch(Sketch sketch)
    {
    	SketchCode[] code = sketch.getCode();
    	
    	if (code.length<1)
    		return false;
    	
    	// modify the code below, replace all numbers with their var names
    	ArrayList<Number> numbers = getAllNumbers(sketch);
    	for (int tab=0; tab<code.length; tab++)
    	{
    		int charInc = 0;
			String c = code[tab].getSavedProgram();
			for (Number n : numbers)
    		{
    			// handle only numbers is the current tab
    			if (n.tabIndex != tab)
    				continue;
    			
    			// replace number value with a variable
    			c = replaceString(c, n.startChar + charInc, n.endChar + charInc, n.name);
    			charInc += n.name.length() - n.value.length();
    		}
			code[tab].setProgram(c);
    	}
    	
    	// add all variable declarations to the first tab
    	String c = code[0].getProgram();
    	String header;

    	header = "\n\n\n\n" +
    		 "/****************************/\n" +
    		 "/* GENERATED BY NUMBERSENSE */\n" +
		 	 "/****************************/\n" +
    		 "\n";

    	for (Number n : numbers)
    	{
    		header += n.type + " " + n.name + " = " + n.value + ";\n";
    	}
    	
    	header += "\n\n\n\n\n";
    	
    	code[0].setProgram(header + c);
    	
    	System.out.println("Modified code:");
    	for (int i=0; i<code.length; i++)
    	{
    		System.out.println("file " + i + "\n=======");
    		System.out.println(code[i].getProgram());
    	}

    	return true;
    }
    
    public ArrayList<Number> getAllNumbers(Sketch sketch)
    {
    	SketchCode[] code = sketch.getCode();
    	int numCount = 0;
    	
    	ArrayList<Number> numbers = new ArrayList<Number>();

    	/* for every number found: 
    	 * save its type (int/float), name, value and position in code.
    	 */
    	String varName = "numbersense_var";
    	for (int i=0; i<code.length; i++)
    	{
    		String c = new String(code[i].getSavedProgram());
    		Pattern p = Pattern.compile("[\\[\\{<>(),\\s\\+\\-\\/\\*^%!|&=]\\d+\\.?\\d*");
    		Matcher m = p.matcher(c);
        
    		while (m.find())
    		{
    			// special case for ignoring (0x...)
    			if (c.charAt(m.end()) == 'x' ||
    				c.charAt(m.end()) == 'X')
    				continue;
    			
    			// special case for ignoring number inside a string ("")
    			if (isInsideString(m.start(), c))
    				continue;
    			
    			String type = "int";
    			String name = varName + "_" + numCount;
    			String value = m.group(0).substring(1, m.group(0).length());
    			int line = countLines(c.substring(0, m.start())) - 1;			// zero based
    			if (value.contains(".")) {
    				type = "float";
    			}
    			
    			numbers.add(new Number(type, name, value, i, line, m.start()+1, m.end()));
    			numCount++;
    		}
    	}

    	return numbers;
    }
    
    private int countLines(String str)
    {
    	String[] lines = str.split("\r\n|\n\r|\n|\r");
    	return lines.length;
    }
    
    private boolean isInsideString(int pos, String str)
    {
    	int quoteNum = 0;
    	
    	for (int c = pos; c>=0 && str.charAt(c) != '\n'; c--)
    	{
    		if (str.charAt(c) == '"')
    			quoteNum++;
    	}
    	
    	if (quoteNum%2 == 1)
    		return true;
    	
    	return false;
    }
    
	public String replaceString(String str, int start, int end, String put)
	{
		return str.substring(0, start) + put + str.substring(end, str.length());
	}
}
