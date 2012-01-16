package uk.ac.ed.inf.mandelbrotmaps;

import android.content.Context;
import android.util.Log;

public class JuliaFractalView extends AbstractFractalView{

	private static final String TAG = "JuliaFractalView";
	
	// Point paramaterising this Julia set
	private double juliaX = 0;
	private double juliaY = 0;
	
	
	public JuliaFractalView(Context context) {
		super(context);

		// Set the "maximum iteration" calculation constants
		// Empirically determined values for Julia sets.
		ITERATION_BASE = 1.58;
		ITERATION_CONSTANT_FACTOR = 6.46;
		
		// Set home area
		homeGraphArea = new MandelbrotJuliaLocation().getJuliaGraphArea();
		
		// How deep a zoom do we allow?
		MAXZOOM_LN_PIXEL = -20; // Beyond -21, "double"s break down(!).
	}
		
		
	public void setJuliaParameter(double newJuliaX, double newJuliaY) {
		juliaX = newJuliaX;
		juliaY = newJuliaY;
		//TODO!!!     parentPanel.refreshCurrentLocation();
		scheduleNewRenders();
	}
	
	public double[] getJuliaParam() {
		double[] juliaParam = new double[2];
		juliaParam[0] = juliaX;
		juliaParam[1] = juliaY;
		return juliaParam;
	}
	
	// Load a location
	void loadLocation(MandelbrotJuliaLocation mjLocation) {
		setScaledIterationCount(mjLocation.getJuliaContrast());
		double[] juliaParam = mjLocation.getJuliaParam();
		setGraphArea(mjLocation.getJuliaGraphArea(), true);
		setJuliaParameter(juliaParam[0], juliaParam[1]);
	}
	
	// Iterate a rectangle of pixels, in range (xPixelMin, yPixelMin) to (xPixelMax, yPixelMax)
	void computePixels(
		int[] outputPixelArray,  // Where pixels are output
		int[] currentPixelSizes,
		int pixelBlockSize,  // Pixel "blockiness"
		final boolean showRenderingProgress,  // Call newPixels() on outputMIS as we go?
		final int xPixelMin,
		final int xPixelMax,
		final int yPixelMin,
		final int yPixelMax,
		final double xMin,
		final double yMax,
		final double pixelSize,
		final boolean allowInterruption,  // Shall we abort if renderThread signals an abort?
		RenderMode renderMode
	) {		
		int maxIterations = getMaxIterations();
		int imgWidth = xPixelMax - xPixelMin;
		
		// Efficiency: For very high-demanding pictures, increase pixel block.
		if (
			(pixelBlockSize!=1) && (maxIterations>10000)
		) pixelBlockSize = Math.min(
			getWidth() / 17,
			pixelBlockSize * (maxIterations/5000)
		);
		
		int xPixel, yPixel, iterationNr;
		double colourCode;
		int colourCodeR, colourCodeG, colourCodeB, colourCodeHex;
		int pixelBlockA, pixelBlockB;
	
		double x, y;
		double newx, newy;
		
		// We don't want the Julia parameter to change under our feet...
		double myJuliaX = juliaX;
		double myJuliaY = juliaY;
		
	
		for (yPixel=yPixelMin; yPixel<yPixelMax+1-pixelBlockSize; yPixel+=pixelBlockSize) {			
			// Detect rendering abortion.			
			if (
				allowInterruption &&
				renderThread.abortSignalled()
			) 
				{
					Log.d("MFV", "Returning based on interruption test");
					Log.d("MFV", "renderThread.abortSignalled() = " + renderThread.abortSignalled());
					return;
				}
		
			for (xPixel=xPixelMin; xPixel<xPixelMax+1-pixelBlockSize; xPixel+=pixelBlockSize) {
				// Initial coordinates
				x = xMin + ( (double)xPixel * pixelSize);
				y = yMax - ( (double)yPixel * pixelSize);
			
				for (iterationNr=0; iterationNr<maxIterations; iterationNr++) {
					// z^2 + c
					newx = (x*x) - (y*y) + myJuliaX;
					newy = (2 * x * y) + myJuliaY;
				
					x = newx;
					y = newy;
				
					// Well known result: if distance is >2, escapes to infinity...
					if ( (x*x + y*y) > 4) break;
				}
				
				// Percentage (0.0 -- 1.0)
				colourCode = (double)iterationNr / (double)maxIterations;
				
				// Red
				colourCodeR = Math.min((int)(255 * 2*colourCode), 255);
				
				// Green
				colourCodeG = (int)(255*colourCode);
				
				// Blue
				colourCodeB = (int)(
					127.5 - 127.5*Math.cos(
						3 * Math.PI * colourCode
					)
				);
				
				// Save colour info for this pixel. int, interpreted: 0xAARRGGBB
				colourCodeHex = (0xFF<<24) + (colourCodeR<<16) + (colourCodeG<<8) + (colourCodeB);
				for (pixelBlockA=0; pixelBlockA<pixelBlockSize; pixelBlockA++) {
					for (pixelBlockB=0; pixelBlockB<pixelBlockSize; pixelBlockB++) {
						if(outputPixelArray == null) return;
						outputPixelArray[imgWidth*(yPixel+pixelBlockB) + (xPixel+pixelBlockA)] = colourCodeHex;
						currentPixelSizes[imgWidth*(yPixel+pixelBlockB) + (xPixel+pixelBlockA)] = pixelBlockSize;
					}
				}
			}
			// Show thread's work in progress
			if ((showRenderingProgress) && (yPixel % 3 == 0)
			) 
				{
					postInvalidate();
				}
		}
		postInvalidate();
		Log.d("MFV", "Reached end of computation loop");
	}


	
	/* Mouse events
	private void changeMode(int modeID) {
		mandelbrotMode = modeID;
	}
	//Mouse click
	public void mouseClicked(MouseEvent e) {
		// Real time mode? Change modes on click.
		if (mandelbrotMode == MODE_REALTIMEJULIA) {
			changeMode(MODE_FREEZE);
			setCursor(null);
		} else if (mandelbrotMode == MODE_FREEZE) {
			changeMode(MODE_REALTIMEJULIA);
			// Generate Julia set immediately.
			mouseMoved(e);
		}
	}	
	public void mouseMoved(MouseEvent e) {
		// Draw Julia set - if in real-time Julia mode, and real-time enabled
		if (
			(mandelbrotMode == MODE_REALTIMEJULIA)
		) {
			double[] mandelbrotGraphArea = getGraphArea();
			double pixelSize = getPixelSize();
		
			// Mouse position, on the complex plane (translated from pixels)
			juliaX = mandelbrotGraphArea[0] + ( (double)e.getX() * pixelSize );
			juliaY = mandelbrotGraphArea[1] - ( (double)e.getY() * pixelSize );
		}
	}
	
	// Need to override for MouseListener; unused at present.
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	*/

}