package dk.statsbiblioteket.medieplatform.newspaper.manualQA.flagging;

import dk.statsbiblioteket.medieplatform.autonomous.Batch;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.DataFileNodeEndsParsingEvent;
import dk.statsbiblioteket.util.xml.DOM;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

public class FlaggingCollectorTest {

    private static final String EMPTY
            = "<manualqainput version=\"0.1\" xmlns=\"http://schemas.statsbiblioteket.dk/manualqainput/\">\n" +
              "    <manualqafiles/>\n" +
              "</manualqainput>";

    @Test
    public void testToReport() throws Exception {
        FlaggingCollector collector = new FlaggingCollector(
                new Batch("B400022028241"), DOM.streamToDOM(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(
                        "batchStructure.xml")), "0.1-SNAPSHOT");


        String report = collector.toReport();
        Assert.assertEquals(report, EMPTY);
        Assert.assertFalse(collector.hasFlags());

        collector.addFlag(
                new DataFileNodeEndsParsingEvent(
                        "B400022028241-RT1/400022028241-1/UNMATCHED/adresseavisen1759-400022028241-1-0008.jp2"),
                "jp2file",
                "testComponent",
                "this is the description");
        Assert.assertNotEquals(collector.toReport(), EMPTY);
        Assert.assertTrue(
                collector.toReport().contains(
                        "        <manualqafile>\n" +
                        "            <filereference>B400022028241-RT1/400022028241-1/UNMATCHED/adresseavisen1759-400022028241-1-0008.jp2</filereference>\n" +
                        "            <type>jp2file</type>\n" +
                        "            <component>testComponent</component>\n" +
                        "            <description>this is the description</description>\n" +
                        "        </manualqafile>"), collector.toReport());
        Assert.assertTrue(collector.hasFlags(), collector.toReport());

        collector.addFlag(
                new AttributeParsingEvent(
                        "B400022028241-RT1/400022028241-1/1795-06-15-02/adresseavisen1759-1795-06-15-02.edition.xml") {
                    @Override
                    public InputStream getData() throws IOException {
                        return null;
                    }

                    @Override
                    public String getChecksum() throws IOException {
                        return null;
                    }
                }, "metadata", "testComponent2", "this is the description2");
        Assert.assertTrue(
                collector.toReport().contains(
                        "        <manualqafile>\n" +
                        "            <filereference>B400022028241-RT1/400022028241-1/1795-06-15-02/adresseavisen1759-1795-06-15-02-0004.jp2</filereference>\n" +
                        "            <type>metadata</type>\n" +
                        "            <component>testComponent2</component>\n" +
                        "            <description>this is the description2</description>\n" +
                        "        </manualqafile>"));

    }


}
