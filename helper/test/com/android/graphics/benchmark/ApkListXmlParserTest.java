package com.android.graphics.benchmark;


import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

@RunWith(JUnit4.class)
public class ApkListXmlParserTest {
    ApkListXmlParser parser = new ApkListXmlParser();

    @Test
    public void testSingleApkInfo() throws ParserConfigurationException, SAXException, IOException {
        try (InputStream input = new ByteArrayInputStream(
                ("<?xml version=\"1.0\"?>\n"
                        + "<apk-info>\n"
                        + "    <apk>\n"
                        + "        <name>foo</name>\n"
                        + "        <fileName>foo.apk</fileName>\n"
                        + "        <packageName>com.foo.test</packageName>\n"
                        + "    </apk>\n"
                        + "</apk-info>\n").getBytes())) {
            List<ApkInfo> apks = parser.parse(input);
            assertEquals(1, apks.size());
            ApkInfo apk = apks.get(0);
            assertEquals("foo", apk.getName());
            assertEquals("foo.apk", apk.getFileName());
            assertEquals("com.foo.test", apk.getPackageName());
        }
    }

    @Test
    public void testApkWithArguments() throws ParserConfigurationException, SAXException, IOException {
        try (InputStream input = new ByteArrayInputStream(
                ("<?xml version=\"1.0\"?>\n"
                        + "<apk-info>\n"
                        + "    <apk>\n"
                        + "        <name>foo</name>\n"
                        + "        <fileName>foo.apk</fileName>\n"
                        + "        <packageName>com.foo.test</packageName>\n"
                        + "        <args>\n"
                        + "            <key1>value1</key1>"
                        + "            <key2 type=\"int\">value2</key2>"
                        + "        </args>\n"
                        + "    </apk>\n"
                        + "</apk-info>\n").getBytes())) {
            List<ApkInfo> apks = parser.parse(input);
            assertEquals(1, apks.size());
            ApkInfo apk = apks.get(0);
            assertEquals("foo", apk.getName());
            assertEquals("foo.apk", apk.getFileName());
            assertEquals("com.foo.test", apk.getPackageName());
            List<ApkInfo.Argument> args = apk.getArgs();
            assertEquals("key1", args.get(0).getKey());
            assertEquals("value1", args.get(0).getValue());
            assertEquals(ApkInfo.Argument.Type.STRING, args.get(0).getType());
            assertEquals("key2", args.get(1).getKey());
            assertEquals("value2", args.get(1).getValue());
            assertEquals(ApkInfo.Argument.Type.INT, args.get(1).getType());
        }
    }

}