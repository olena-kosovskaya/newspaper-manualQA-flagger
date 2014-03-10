package dk.statsbiblioteket.medieplatform.newspaper.manualQA;

import dk.statsbiblioteket.medieplatform.autonomous.ResultCollector;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.eventhandlers.DefaultTreeEventHandler;
import dk.statsbiblioteket.medieplatform.newspaper.manualQA.flagging.FlaggingCollector;
import dk.statsbiblioteket.util.Pair;

/**
 * At the time of implementing this, all histograms we have seen had a very big spike for a color number 0, 1, or 2. It seems
 * this cannot be avoided. This is not a spike in the normal curve-wise sense. It is a single color that juts up as a pin in the
 * graph. The same could be the case at the other end. If this spike is too high, say 2% of the total number of pixels, the image
 * should be flagged.
 */
public class EndSpikeHistogramChecker extends DefaultTreeEventHandler {
    private dk.statsbiblioteket.medieplatform.newspaper.manualQA.flagging.FlaggingCollector flaggingCollector;
    private ResultCollector resultCollector;
    private double threshold;

    private int minColorConsideredBlack;
    private int maxColorConsideredBlack;
    private int minColorConsideredWhite;
    private int maxColorConsideredWhite;
    private double maxPercentAllowedNearBlack;
    private double maxPercentAllowedNearWhite;

    public EndSpikeHistogramChecker(ResultCollector resultCollector, FlaggingCollector flaggingCollector, double threshold,
                                    int minColorConsideredBlack, int maxColorConsideredBlack, int minColorConsideredWhite,
                                    int maxColorConsideredWhite, double maxPercentAllowedNearBlack,
                                    double maxPercentAllowedNearWhite) {
        this.flaggingCollector = flaggingCollector;
        this.resultCollector = resultCollector;
        this.threshold = threshold;
        this.minColorConsideredBlack = minColorConsideredBlack;
        this.maxColorConsideredBlack = maxColorConsideredBlack;
        this.minColorConsideredWhite = minColorConsideredWhite;
        this.maxColorConsideredWhite = maxColorConsideredWhite;
        this.maxPercentAllowedNearBlack = maxPercentAllowedNearBlack;
        this.maxPercentAllowedNearWhite = maxPercentAllowedNearWhite;
    }

    @Override
    public void handleAttribute(AttributeParsingEvent event) {
        try {
            if (event.getName().endsWith(".film.histogram.xml")) {
                Histogram histogram = new Histogram(event.getData());
                Pair<Spike, Long> spikeAndTotal = findHighestSpike(histogram);
                Spike spike = spikeAndTotal.getLeft();
                Long total = spikeAndTotal.getRight();
                double amount = (spike.getValue() + 0.0) / total;
                if (amount > threshold) {
                    flaggingCollector.addFlag(
                            event,
                            "jp2file",
                            getComponent(),
                            "Found suspicious color spike for color '" + spike.getColor() + "'. " + amount * 100
                                    + "% of the graph is contained in this spike.");
                }

                double lowLightPercent = percentColorsInRange(minColorConsideredBlack, maxColorConsideredBlack, histogram);
                double highLightPercent = percentColorsInRange(minColorConsideredWhite, maxColorConsideredWhite, histogram);
                String lowColorForPrint = minColorConsideredBlack
                        + (minColorConsideredBlack == maxColorConsideredBlack ? "" : "-" + maxColorConsideredBlack);
                String highColorForPrint = minColorConsideredWhite
                        + (minColorConsideredWhite == maxColorConsideredWhite ? "" : "-" + maxColorConsideredWhite);

                if (lowLightPercent > maxPercentAllowedNearBlack) {
                    flaggingCollector.addFlag(
                            event,
                            "jp2file",
                            getComponent(),
                            "Found possible low-light blowout: more than " + maxPercentAllowedNearBlack
                                    + "% pixels of color " + lowColorForPrint + ", found " + lowLightPercent + "%");
                }

                if (highLightPercent > maxPercentAllowedNearWhite) {
                    flaggingCollector.addFlag(
                            event,
                            "jp2file",
                            getComponent(),
                            "Found possible high-light blowout: more than " + maxPercentAllowedNearWhite
                                    + "% pixels of color " + highColorForPrint + ", found " + highLightPercent + "%");
                }
            }
        } catch (Exception e) {
            resultCollector.addFailure(event.getName(), "exception", getComponent(), e.getMessage());
        }

    }

    /**
     * Calculate the percentage of the total amount of pixels in the given histogram that lie in the given range.
     * @param minColor The lowest (leftmost) position in the wanted range of the histogram.
     * @param maxColor The highest (rightmost) position in the wanted range of the histogram.
     * @param histogram The histogram wherein the given range lies
     * @return The percentage of the total amount of pixels in the given histogram that lie in the given range.
     */
    private double percentColorsInRange(int minColor, int maxColor, Histogram histogram) {
        double totalColorCount = 0;
        double intervalColorCount = 0;

        long[] values = histogram.values();
        for (int i = 0; i < values.length; i++) {
            totalColorCount += values[i];
            if (minColor <= i && i <= maxColor) {
                intervalColorCount += values[i];
            }
        }

        return (intervalColorCount / totalColorCount) * 100;
    }

    /**
     * Find highest spike that would not be found by the low-light or high-light checks
     * @param histogram Histogram in which such a highest spike should be found
     * @return The highest spike, and the total of examined histogram-values, as a pair
     */
    private Pair<Spike, Long> findHighestSpike(Histogram histogram) {
        long total = 0;
        Spike highest = null;

        long[] values = histogram.values();
        for (int i = maxColorConsideredBlack + 1; i < minColorConsideredWhite - 1; i++) {
            long value = values[i];
            Spike spike = new Spike(i, value);
            if (highest == null || highest.compareTo(spike) < 0) {
                highest = spike;
            }
            total += value;
        }
        return new Pair<>(highest, total);
    }

    private String getComponent() {
        return getClass().getSimpleName();
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with equals
     */
    private class Spike implements Comparable<Spike> {
        private int color;
        private long value;

        private Spike(int color, long value) {
            this.color = color;
            this.value = value;
        }

        public int getColor() {
            return color;
        }

        public long getValue() {
            return value;
        }

        @Override
        public int compareTo(Spike that) {
            return new Long(getValue()).compareTo(that.getValue());
        }
    }
}
