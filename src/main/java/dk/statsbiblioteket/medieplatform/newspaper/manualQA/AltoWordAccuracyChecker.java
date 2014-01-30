package dk.statsbiblioteket.medieplatform.newspaper.manualQA;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import dk.statsbiblioteket.medieplatform.autonomous.ResultCollector;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeEndParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.eventhandlers.DefaultTreeEventHandler;
import dk.statsbiblioteket.medieplatform.newspaper.manualQA.flagging.FlaggingCollector;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.XPathSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * This handler operates at the film-level and computes running averages of the ABBYY Predicted Accuracy for both
 * the whole film and each edition on the film.
 */
public class AltoWordAccuracyChecker extends DefaultTreeEventHandler {

    static Logger logger = LoggerFactory.getLogger(AltoWordAccuracyChecker.class);

    ResultCollector resultCollector;
    FlaggingCollector flaggingCollector;

    Double minimumAcceptable;
    boolean ignoreZero;
    Double minimumAcceptablePerfile;

    /**
     * Map from an eventId to the running average accuracy of all alto files within the corresponding directory.
     */
    Map<String, RunningAverage> averages;
    private static final XPathSelector xpath = DOM.createXPathSelector("alto", "http://www.loc.gov/standards/alto/ns-v2#");

    public AltoWordAccuracyChecker(ResultCollector resultCollector, FlaggingCollector flaggingCollector, Properties properties) {
        this.resultCollector = resultCollector;
        this.flaggingCollector = flaggingCollector;
        averages = new HashMap<String, RunningAverage>();
        minimumAcceptable = Double.parseDouble(properties.getProperty(ConfigConstants.MINIMUM_ALTO_AVERAGE_ACCURACY));
        minimumAcceptablePerfile = Double.parseDouble(properties.getProperty(ConfigConstants.MINIMUM_ALTO_PERFILE_ACCURACY));
        ignoreZero = Boolean.parseBoolean(properties.getProperty(ConfigConstants.ALTO_IGNORE_ZERO_ACCURACY));
    }

    public static class RunningAverage {
        private Double currentValue;
        private long count;

        public RunningAverage() {
            currentValue = 0.0;
            count = 0L;
        }

        public void addValue(double value){
            currentValue = (count*currentValue + value)/(count + 1);
            count++;
        }

        public Double getCurrentValue() {
            return currentValue;
        }
    }

    @Override
    public void handleAttribute(AttributeParsingEvent event) {
        if (!event.getName().endsWith("alto.xml")) {
            return;
        }
        Double accuracy = null;
        try {
            accuracy = readAccuracy(event);
        } catch (NumberFormatException e) {
            flaggingCollector.addFlag(event, "metadata", getClass().getSimpleName(), "No ACCURACY attribute found in alto:Page" +
                    " element in this file.");
            return;
        }

        if (accuracy < minimumAcceptablePerfile && (!ignoreZero || accuracy > 0)) {
            flaggingCollector.addFlag(event, "metadata", getClass().getSimpleName(), "Accuracy for alto file is less " +
                    "than the prescribed minimum (" + minimumAcceptablePerfile + ") :" + accuracy);
        }

        String editionId = event.getName().substring(0, event.getName().lastIndexOf('/'));
        String filmId = editionId.substring(0, editionId.lastIndexOf('/'));

        //These initialisations could equivalently be made in the handleNodeBegin method for the given edition and film
        //nodes. Putting them here, in the first alto attribute for each node, requires less work because we don't need
        //to identify edition and film nodes by regexps. They're just the parent and grandparent of the current node.
        if (averages.get(editionId) == null) {
            averages.put(editionId, new RunningAverage());
        }
        if (averages.get(filmId) == null) {
            averages.put(filmId, new RunningAverage());
        }
        if (accuracy > 0 || !ignoreZero) {
            averages.get(editionId).addValue(accuracy);
            averages.get(filmId).addValue(accuracy);
        }
    }


    @Override
    public void handleNodeEnd(NodeEndParsingEvent event) {
        String eventName = event.getName();
        RunningAverage runningAverage = averages.get(eventName);
        if (runningAverage == null) {
            //this happens if we are at the end of any node other than a film or edition node.
            return;
        }
        String lastElement = eventName.substring(eventName.lastIndexOf("/"));
        String type;
        if (lastElement.matches(".*-.*-.*-.*")) {
            type = "Edition";
        } else {
            type = "Film";
        }
        logger.debug("Average accuracy for the " + type + " " + event.getName() + " is " + runningAverage.getCurrentValue() + " from (" + runningAverage.count + ")");
        if (runningAverage.getCurrentValue() < minimumAcceptable) {
            flaggingCollector.addFlag(event, "metadata", getClass().getSimpleName(), "Average OCR accuracy for this " + type + " is less" +
                    " than the minimum expected (" + minimumAcceptable + ") : " + runningAverage.getCurrentValue());
        }
        //Remove the event from the map so it can be garbage collected.
        averages.remove(event.getName());
    }

    public static Double readAccuracy(AttributeParsingEvent event) throws NumberFormatException {
        Document doc;
        try {
            doc = DOM.streamToDOM(event.getData(), true);
            if (doc == null) {
                throw new RuntimeException("Could not parse xml");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final String accuracyXPath="alto:alto/alto:Layout/alto:Page/@ACCURACY";
        String accuracyString = xpath.selectString(doc, accuracyXPath);
        return Double.parseDouble(accuracyString);
    }
}
