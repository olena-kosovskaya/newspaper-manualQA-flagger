package dk.statsbiblioteket.medieplatform.newspaper.manualQA;

import dk.statsbiblioteket.medieplatform.autonomous.Batch;
import dk.statsbiblioteket.medieplatform.autonomous.ResultCollector;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeBeginsParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeEndParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.eventhandlers.DefaultTreeEventHandler;
import dk.statsbiblioteket.medieplatform.newspaper.manualQA.flagging.FlaggingCollector;


public class DarknessHistogramChecker extends DefaultTreeEventHandler {
    private final ResultCollector resultCollector;
    private final FlaggingCollector flaggingCollector;
    private Batch batch;

    int numberOfTooDarkImages;
    int maxNumberOfDarkImagesAllowed;
    int lowestHistogramIndexNotConsideredBlack;
    int lowestAcceptablePeakPosition;

    public DarknessHistogramChecker(ResultCollector resultCollector, FlaggingCollector flaggingCollector, Batch batch,
                                    int maxNumberOfDarkImagesAllowed, int lowestHistogramIndexNotConsideredBlack,
                                    int lowestAcceptablePeakPosition) {
        this.resultCollector = resultCollector;
        this.flaggingCollector = flaggingCollector;
        this.batch = batch;
        this.maxNumberOfDarkImagesAllowed = maxNumberOfDarkImagesAllowed;
        this.lowestHistogramIndexNotConsideredBlack = lowestHistogramIndexNotConsideredBlack;
        this.lowestAcceptablePeakPosition = lowestAcceptablePeakPosition;
    }


    @Override
    public void handleAttribute(AttributeParsingEvent event) {
        try {
            if (event.getName().endsWith(".histogram.xml")) {
                // For each histogram
                long[] histogram = new Histogram(event.getData()).values();

                if (histogramIsTooDark(histogram)) {
                    numberOfTooDarkImages++;
                }
            }
        } catch (Exception e) {
            resultCollector.addFailure(event.getName(), "exception", getComponent(), e.getMessage());
        }
    }


    private boolean histogramIsTooDark(long[] histogram) {
        // TODO ignore (i.e. return false) if there is a very small amount of text on the image

        // Find highest value that is not considered black
        long highestPeakValueFound = 0;
        int highestPeakPosition = 255;
        for (int i = lowestHistogramIndexNotConsideredBlack; i < 256; i++) {
            if (histogram[i] > highestPeakValueFound) {
                highestPeakValueFound = histogram[i];
                highestPeakPosition = i;
            }
        }

        // If it is too far to the "left" on the histogram, mark as too dark
        if (highestPeakPosition < lowestAcceptablePeakPosition) {
            return true;
        }

        return false;
    }


    private String getComponent() {
        return getClass().getName();
    }


    @Override
    public void handleNodeBegin(NodeBeginsParsingEvent event) {
        try {
            if (event.getName().matches("/" + batch.getBatchID() + "-" + "[0-9]{2}$")) {
                // We have now entered a film, so initialize
                numberOfTooDarkImages = 0;
            }
        } catch (Exception e) {
            resultCollector.addFailure(event.getName(), "exception", getComponent(), e.getMessage());
        }
    }


    @Override
    public void handleNodeEnd(NodeEndParsingEvent event) {
        try {
            if (event.getName().matches("/" + batch.getBatchID() + "-" + "[0-9]{2}$")) {
                // We have now left a film, so flag if there were too many dark pages
                flaggingCollector.addFlag(event, "jp2file", getComponent(),
                        "This film has a high number of dark images! " + numberOfTooDarkImages + " images were found that appear"
                + " dark. You get this message because the number is higher than " + maxNumberOfDarkImagesAllowed + ".");
            }
        } catch (Exception e) {
            resultCollector.addFailure(event.getName(), "exception", getComponent(), e.getMessage());
        }
    }
}
