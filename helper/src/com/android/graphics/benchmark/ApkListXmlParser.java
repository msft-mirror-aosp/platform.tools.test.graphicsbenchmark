/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.graphics.benchmark;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Parser to read apk-info.xml.
 */
public class ApkListXmlParser {

    public ApkListXmlParser() {
    }

    public List<ApkInfo> parse(File file)
            throws IOException, ParserConfigurationException, SAXException {
        try (InputStream is = new FileInputStream(file)) {
            return parse(is);
        }
    }

    public List<ApkInfo> parse(InputStream inputStream)
            throws IOException, ParserConfigurationException, SAXException {
        // TODO: Need error checking.
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(inputStream);
        doc.getDocumentElement().normalize();
        NodeList nodes = doc.getElementsByTagName("apk");
        List<ApkInfo> apks = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                apks.add(createApkInfo(element));
            }
        }
        return apks;
    }

    private ApkInfo createApkInfo(Element element) {
        List<ApkInfo.Argument> args = new ArrayList<>();
        NodeList argsNodeList = element.getElementsByTagName("args");
        if (argsNodeList.getLength() > 0) {
            NodeList children = argsNodeList.item(0).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node argNode = children.item(j);
                if (argNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element argElement = (Element) argNode;
                args.add(new ApkInfo.Argument(
                        argElement.getTagName(), argElement.getTextContent()));
            }
        }

        return new ApkInfo(
                getElement(element, "name", null),
                getElement(element, "fileName", null),
                getElement(element, "packageName", null),
                getElement(element, "layerName", null),
                args,
                Integer.parseInt(getElement(element, "runTime", "10000"))
                );
    }

    private String getElement(Element element, String tag, String defaultValue) {
        NodeList elements = element.getElementsByTagName(tag);
        if (elements.getLength() > 0) {
            return elements.item(0).getTextContent();
        } else {
            return defaultValue;
        }
    }
}
