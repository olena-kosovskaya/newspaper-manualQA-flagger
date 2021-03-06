package dk.statsbiblioteket.medieplatform.newspaper.manualQA;

import dk.statsbiblioteket.medieplatform.autonomous.Batch;
import dk.statsbiblioteket.medieplatform.autonomous.ResultCollector;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.newspaper.manualQA.flagging.FlaggingCollector;
import dk.statsbiblioteket.medieplatform.newspaper.manualQA.mockers.MixMocker;
import dk.statsbiblioteket.util.xml.DOM;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Test that we can identify issues with mix files.
 */
public class MixHandlerTest {

    /**
     * An extra software version that can be added to the mix file. (Because this is a repeatable element.)
     */
    String extraSoftware = "            <mix:ScanningSystemSoftware><!--Repeatable-->\n" +
            "                <mix:scanningSoftwareName>___softwares___</mix:scanningSoftwareName>\n" +
            "                <mix:scanningSoftwareVersionNo>___versions___</mix:scanningSoftwareVersionNo>\n" +
            "            </mix:ScanningSystemSoftware>\n";

    Properties properties;
    AttributeParsingEvent event;
    FlaggingCollector flaggingCollector;
    ResultCollector resultCollector;


    /**
     * Set up the mix.xml to be validated and the properties specifying the expected values. Individual tests can
     * overwrite these property values to trigger different flags.
     */
    @BeforeMethod
    public void setUp() {
        properties = new Properties();
        properties.setProperty(ConfigConstants.IMAGE_PRODUCERS, "Statsbiblioteket,Bob,Anand");
        properties.setProperty(ConfigConstants.SCANNER_MANUFACTURERS, "AcmeCorp");
        properties.setProperty(ConfigConstants.SCANNER_MODEL_NUMBERS, "AK47,Mark2");
        properties.setProperty(ConfigConstants.SCANNER_MODELS, "Nutrimat");
        properties.setProperty(ConfigConstants.SCANNER_SERIAL_NOS, "h2s04,h2r07,h2z12");
        properties.setProperty(ConfigConstants.SCANNER_SOFTWARES, "vi;0.0.1beta,emacs;0.0.2alpha");
        properties.setProperty(ConfigConstants.MIN_IMAGE_WIDTH, "2000");
        properties.setProperty(ConfigConstants.MAX_IMAGE_WIDTH, "15000");
        properties.setProperty(ConfigConstants.MIN_IMAGE_HEIGHT, "4000");
        properties.setProperty(ConfigConstants.MAX_IMAGE_HEIGHT, "12000");
        Batch batch = new Batch();
        flaggingCollector = new FlaggingCollector(batch, DOM.streamToDOM(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(
                        "batchStructure.xml")), "42.24", 100);
        resultCollector = new ResultCollector("foo", "bar");

        final String additionalSoftware = extraSoftware.replace("___softwares___", "emacs").replace("___versions___",
                "0.0.2alpha");
        final String xml =  MixMocker.getMixXml(
                "AcmeCorp",
                "Nutrimat",
                "AK47",
                "h2r07",
                "vi",
                "0.0.1beta",
                "Statsbiblioteket;Anand",
                additionalSoftware,
                "6121",
                "8661"
        );
        event = new AttributeParsingEvent("B400022028241-RT1/400022028241-1/1795-06-15-01/"
                + "adresseavisen1759-1795-06-15-01-0003A.mix.xml") {
            @Override
            public InputStream getData() throws IOException {
                return new ByteArrayInputStream(xml.getBytes());
            }

            @Override
            public String getChecksum() throws IOException {
                return null;
            }
        };
    }

    /**
     * The case where no flags are raised.
     */
    @Test
    public void testHandleNoFlag() {
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        assertFalse(flaggingCollector.hasFlags(), flaggingCollector.toReport());
        assertTrue(resultCollector.isSuccess());
    }

    /**
     * The following tests cover all the cases where a flag can be raised because a parameter has a
     * previously-unknown value.
     */

    @Test
    public void testHandleModelSerialNumbers() {
        properties.setProperty(ConfigConstants.SCANNER_SERIAL_NOS, "h2s04,h2s05");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 1);
        assertTrue(report.contains("h2r07"));
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess());
    }

    /**
     * Tests that multiple new files with the same new model number give only one flag.
     */
    @Test
    public void testHandleModelSerialNumbersMultipleSame() {
        properties.setProperty(ConfigConstants.SCANNER_SERIAL_NOS, "h2s04,h2s05");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 1);
        assertTrue(report.contains("h2r07"));
        assertTrue(report.contains("2 time(s)"), report);
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess());
    }

    @Test
    public void testHandleModel() {
        properties.setProperty(ConfigConstants.SCANNER_MODELS, "MODEL_A,MODEL_B");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 1, report);
        assertTrue(report.contains("Nutrimat"), report);
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess(), resultCollector.toReport());
    }

    /**
     * Tests that two files with two new model names gives two flags.
     */
    @Test
    public void testHandleModelMultipleDifferent() {
        properties.setProperty(ConfigConstants.SCANNER_MODELS, "MODEL_A,MODEL_B");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        final String newXml =  MixMocker.getMixXml(
                "AcmeCorp",
                "AcmeMax",
                "AK47",
                "h2r07",
                "vi",
                "0.0.1beta",
                "Statsbiblioteket;Anand",
                "",
                "6121",
                "8661"
        );
        AttributeParsingEvent secondNewModelEvent = new AttributeParsingEvent(event.getName().replace("3A", "3B")) {
            @Override
            public InputStream getData() throws IOException {
                return new ByteArrayInputStream(newXml.getBytes());
            }

            @Override
            public String getChecksum() throws IOException {
                throw new RuntimeException("not implemented");
            }
        };
        mixHandler.handleAttribute(secondNewModelEvent);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 2, report);
        assertTrue(report.contains("Nutrimat"), report);
        assertTrue(report.contains("AcmeMax"));
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess(), resultCollector.toReport());
    }

    @Test
    public void testHandleProducer() {
        properties.setProperty(ConfigConstants.IMAGE_PRODUCERS, "Anand, Sigismund");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 1, report);
        assertTrue(report.contains("Statsbiblioteket"), report);
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess(), resultCollector.toReport());
    }

    @Test
    public void testHandleModelNumber() {
        properties.setProperty(ConfigConstants.SCANNER_MODEL_NUMBERS, "AK48,Mark2");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 1, report);
        assertTrue(report.contains("AK47"), report);
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess(), resultCollector.toReport());
    }

    @Test
    public void testHandleAttributeFlagSoftwareVersion() throws Exception {
        properties.setProperty(ConfigConstants.SCANNER_SOFTWARES, "vi;0.0.1alpha,emacs;0.0.2alpha");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        mixHandler.handleFinish();
        String report = flaggingCollector.toReport();
        assertEquals(report.split("<manualqafile>").length - 1, 1, report);
        assertTrue(report.contains("vi;0.0.1beta"), report);
        assertTrue(flaggingCollector.hasFlags(), report);
        assertTrue(resultCollector.isSuccess(), resultCollector.toReport());
    }

    @Test
    public void testHandleIdmageWidthTooLow() {
        properties.setProperty(ConfigConstants.MIN_IMAGE_WIDTH, "7000");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        assertTrue(flaggingCollector.hasFlags(), flaggingCollector.toReport());
        assertTrue(resultCollector.isSuccess());
    }

    @Test
    public void testHandleIdmageWidthTooHigh() {
        properties.setProperty(ConfigConstants.MAX_IMAGE_WIDTH, "4000");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        assertTrue(flaggingCollector.hasFlags(), flaggingCollector.toReport());
        assertTrue(resultCollector.isSuccess());
    }

    @Test
    public void testHandleIdmageHeightTooLow() {
        properties.setProperty(ConfigConstants.MIN_IMAGE_HEIGHT, "9000");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        assertTrue(flaggingCollector.hasFlags(), flaggingCollector.toReport());
        assertTrue(resultCollector.isSuccess());
    }

    @Test
    public void testHandleIdmageHeightTooHigh() {
        properties.setProperty(ConfigConstants.MAX_IMAGE_HEIGHT, "8000");
        MixHandler mixHandler= new MixHandler(resultCollector, properties, flaggingCollector);
        mixHandler.handleAttribute(event);
        assertTrue(flaggingCollector.hasFlags(), flaggingCollector.toReport());
        assertTrue(resultCollector.isSuccess());
    }


}
