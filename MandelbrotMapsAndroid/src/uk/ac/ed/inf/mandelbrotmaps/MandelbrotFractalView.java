package uk.ac.ed.inf.mandelbrotmaps;

import uk.ac.ed.inf.mandelbrotmaps.AbstractFractalView.FractalViewSize;
import uk.ac.ed.inf.mandelbrotmaps.RenderThread.FractalSection;
import uk.ac.ed.inf.mandelbrotmaps.colouring.ColouringScheme;
import uk.ac.ed.inf.mandelbrotmaps.colouring.RGBWalkColouringScheme;
import uk.ac.ed.inf.mandelbrotmaps.colouring.SpiralRenderer;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

public class MandelbrotFractalView extends AbstractFractalView{

	private final String TAG = "MMaps";
	
	private String viewName = "Mandelbrot";
	
	ColouringScheme colourer = new SpiralRenderer();
	
	public MandelbrotFractalView(Context context, RenderStyle style, FractalViewSize size) {
		super(context, style, size);
		
		upperRenderThread.setName("Mandelbrot primary thread");
		lowerRenderThread.setName("Mandelbrot seconary thread");
		
		// Set the "maximum iteration" calculation constants
		// Empirically determined values for Mandelbrot set.
		ITERATION_BASE = 1.24;
		ITERATION_CONSTANT_FACTOR = 54;
		
		// Set home area
		homeGraphArea = new MandelbrotJuliaLocation().getMandelbrotGraphArea();
		
		// How deep a zoom do we allow?
		MAXZOOM_LN_PIXEL = -31; // Beyond -31, "double"s break down(!).
	}
		
		
	// Load a location
	void loadLocation(MandelbrotJuliaLocation mjLocation) {
		setScaledIterationCount(mjLocation.getMandelbrotContrast());
		setGraphArea(mjLocation.getMandelbrotGraphArea(), true);
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
		RenderMode renderMode,
		FractalSection section
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
		
		int xPixel = 0, yPixel = 0, yIncrement = 0, iterationNr = 0;
		double colourCode;
		int colourCodeR, colourCodeG, colourCodeB, colourCodeHex;
		int pixelBlockA, pixelBlockB;
	
		// c = (x0) + (y0)i
		double x0, y0;
	
		// z = (x) + (y)i
		double x, y;
	
		// newz = (newx) + (newy)i
		// ... NB: newz = (z^2 + c)
		double newx, newy;
		
		long initialMillis = System.currentTimeMillis();
		Log.d(TAG, "Initial time: " + initialMillis);
		
		int pixelIncrement = pixelBlockSize;
		if (section != FractalSection.ALL)
			pixelIncrement = 2*pixelBlockSize;
	
		int skippedCount = 0;
		
		int colourFromRenderer;
		
		for (yIncrement = yPixelMin; yIncrement < yPixelMax+1-pixelBlockSize; yIncrement+= pixelIncrement) {			
			//Work backwards on upper half
/*			if (section == FractalSection.UPPER)
				yPixel = yPixelMax - yIncrement - 1;
			else */
				yPixel = yIncrement;
			
			if (
				allowInterruption &&
				upperRenderThread.abortSignalled()
			) 
				{
					Log.d("MFV", "Returning based on interruption test");
					return;
				}
			
			// Set y0 (im part of c)
			y0 = yMax - ( (double)yPixel * pixelSize );			
		
			for (xPixel=xPixelMin; xPixel<xPixelMax+1-pixelBlockSize; xPixel+=pixelBlockSize) {
				//Check to see if this pixel is already iterated to the necessary block size
				if(/*renderMode == RenderMode.JUST_DRAGGED && */
						pixelSizes[(imgWidth*yPixel) + xPixel] <= pixelBlockSize)
				{
					skippedCount++;
					continue;
				}
				
				// Set x0 (real part of c)
				x0 = xMin + ( (double)xPixel * pixelSize);
			
				// Start at x0, y0
				x = x0;
				y = y0;
				
				boolean inside = false;
			
				for (iterationNr=0; iterationNr<maxIterations; iterationNr++) {
					// z^2 + c
					newx = (x*x) - (y*y) + x0;
					newy = (2 * x * y) + y0;
				
					x = newx;
					y = newy;
				
					// Well known result: if distance is >2, escapes to infinity...
					if ( (x*x + y*y) > 4) {
						inside = true;
						break;
					}
				}
				
				/*if (inside)
					colourFromRenderer = colourer.colourOutsidePoint(iterationNr);
				else
					colourFromRenderer = colourer.colourInsidePoint();*/
				
				// Percentage (0.0 -- 1.0)
				colourCode = (double)iterationNr / (double)maxIterations;
				
				// Red
				colourCodeR = Math.min((int)(255 * 6*colourCode), 255);
				
				// Green
				colourCodeG = (int)(255*colourCode);
				
				// Blue
				colourCodeB = (int)(
					127.5 - 127.5*Math.cos(
						7 * Math.PI * colourCode
					)
				);
				
				/*if (iterationNr == 0){
		            colourCodeHex = 0xFF000000;
		        }
				else {
			        //calculate theta - 2pi represents 255 iterations
			        double theta = (double) ((double)iterationNr / (double)255) * 2 * Math.PI;
			        
			        //compute r
			        double x2 = theta * (2.0 * (Math.cos(theta) + 1));
	
			        //compute x
			        double r = theta;
	
			        //compute y
			        double y2 = theta * (2.0 * (Math.sin(theta) + 1));        
			        
			        //defines the number of colours used in each component of RGB
			        int colourRange = 230;
			        //the starting point in each compenent of RGB
			        int startColour = 25;
	
			        //compute the red compnent
			        colourCodeR = (int) (colourRange * r);
			        colourCodeR = boundColour(colourCodeR, colourRange);
			        colourCodeR += startColour;
			        
			        //compute the green component
			        colourCodeG = (int) (colourRange * y2);
			        colourCodeG = boundColour(colourCodeG, colourRange);
			        colourCodeG += startColour;
			        
			        //compute the blue component
			        colourCodeB = (int) (colourRange * x2);
			        colourCodeB = boundColour(colourCodeB, colourRange);
			        colourCodeB += startColour;
	
			        //compute colour from the three components
*/			        colourCodeHex = (0xFF << 24) + (colourCodeR << 16) + (colourCodeG << 8) + (colourCodeB);
				//}
				
				//Note that the pixel being calculated has been calculated in full (upper right of a block)
				currentPixelSizes[(imgWidth*yPixel) + (xPixel)] = DEFAULT_PIXEL_SIZE;
				
				// Save colour info for this pixel. int, interpreted: 0xAARRGGBB				
				for (pixelBlockA=0; pixelBlockA<pixelBlockSize; pixelBlockA++) {
					for (pixelBlockB=0; pixelBlockB<pixelBlockSize; pixelBlockB++) {
						if(outputPixelArray == null) return;
						outputPixelArray[imgWidth*(yPixel+pixelBlockB) + (xPixel+pixelBlockA)] = colourCodeHex;
					}
				}
			}
			// Show thread's work in progress
			if ((showRenderingProgress) && (yPixel % LINES_TO_DRAW_AFTER == 0)) 
				{
					postInvalidate();
				}
		}
		
		postInvalidate();
		Log.d(TAG, "Reached end of computation loop. Skipped: " + skippedCount);
		Log.d(TAG, section.name() + ". Time elapsed: " + (System.currentTimeMillis() - initialMillis));
	}
	
	
	private int boundColour(int colour, int colourRange){
        if (colour > (colourRange * 2)){ 
            int i = (int) (colour / (colourRange * 2));

            colour = colour - (colourRange * 2 * i);
        }
        if (colour > (colourRange)){
            colour = colourRange - (colour - colourRange);
        }
        
        return colour;
    }
	
	
	public double[] getJuliaParams(float touchX, float touchY)
	{
		double[] mandelbrotGraphArea = getGraphArea();
		double pixelSize = getPixelSize();
	
		double[] juliaParams = new double[2];
		
		// Mouse position, on the complex plane (translated from pixels)
		juliaParams[0] = mandelbrotGraphArea[0] + ( (double)touchX * pixelSize );
		juliaParams[1] = mandelbrotGraphArea[1] - ( (double)touchY * pixelSize );
		
		return juliaParams;
	}	
}
