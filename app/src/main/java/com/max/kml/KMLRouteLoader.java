package com.max.kml;

import com.max.latlng.LatLngHelper;
import com.max.logic.XY;
import com.max.route.PointOfInterest;
import com.max.route.PathType;
import com.max.route.Route;
import com.max.route.RouteSegment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class KMLRouteLoader {

    public Route loadRoute(InputStream is) throws InvalidKMLException {
        Document doc = readXMLDocument(is);

        List<RouteSegment> segments = new ArrayList<>();
        List<PointOfInterest> pois = new ArrayList<>();

        NodeList placemarks = doc.getElementsByTagName("Placemark");
        for (int k = 0; k < placemarks.getLength(); k++) {
            String name = null;
            PathType pathType = null;
            List<XY> segment = null;
            XY poi = null;

            Node placemark = placemarks.item(k);
            Node domNode = placemark.getFirstChild();
            while (domNode != null) {
                if (domNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) domNode;
                    if ("name".equals(element.getTagName())) {
                        name = element.getTextContent().trim();
                    } else if ("styleUrl".equals(element.getTagName())) {
                        String style = element.getTextContent().trim();
                        switch (style) {
                            case "#Paved" : pathType = PathType.MAJOR_ROAD; break;
                            case "#Dirt" : pathType = PathType.MINOR_ROAD; break;
                            case "#marker" : break;
                            default : throw new InvalidKMLException("Unknown style '" + style + "'");
                        }
                    } else if ("Point".equals(element.getTagName())) {
                        poi = readPOI(element);
                    } else if ("LineString".equals(element.getTagName())) {
                        segment = readSegment(element);
                    }
                }
                domNode = domNode.getNextSibling();
            }

            if ((segment == null && poi == null) || (segment != null && poi != null))
                throw new InvalidKMLException("Expected one (and only one) Point or LineString in Placemark '" + name + "'");
            if (segment != null) {
                if (pathType == null)
                    throw new InvalidKMLException("Missing path type for segment '" + name + "'");
                segments.add(new RouteSegment(name, pathType, segment));
            } else {
                System.out.println(poi);
                pois.add(new PointOfInterest(name, poi.x, poi.y));
            }
        }

        return new Route(segments, pois, new ArrayList<PointOfInterest>());
    }

    XY readPOI(Element point) throws InvalidKMLException {
        Node domNode = point.getFirstChild();
        while (domNode != null) {
            if (domNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) domNode;
                if ("coordinates".equals(element.getTagName()))
                    return parseCoordinate(element.getTextContent());
            }
            domNode = domNode.getNextSibling();
        }
        throw new InvalidKMLException("Did not find coordinate in point: " + point);
    }

    List<XY> readSegment(Element lineString) throws InvalidKMLException {
        Node domNode = lineString.getFirstChild();
        while (domNode != null) {
            if (domNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) domNode;
                if ("coordinates".equals(element.getTagName())) {
                    List<XY> coords = new ArrayList<>();
                    for (String coordStr : element.getTextContent().split("\\r?\\n"))
                        if (!coordStr.trim().isEmpty())
                            coords.add(parseCoordinate(coordStr));
                    if (coords.size() < 2)
                        throw new InvalidKMLException("Unexpected length of line string: " + coords);
                    return coords;
                }
            }
            domNode = domNode.getNextSibling();
        }
        throw new InvalidKMLException("Did not find coordinates in line string: " + lineString);
    }

    XY parseCoordinate(String coordinateStr) throws InvalidKMLException {
        String[] coordPart = coordinateStr.split(",");
        try {
            double lng = Double.parseDouble(coordPart[0]);
            double lat = Double.parseDouble(coordPart[1]);
            return LatLngHelper.getXYFromLatLng(lat, lng);
        } catch (Exception e) {
            throw new InvalidKMLException("Invalid coordinate: " + coordinateStr, e);
        }
    }

    static Document readXMLDocument(InputStream is) throws InvalidKMLException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(is);
        } catch (Exception e) {
            throw new InvalidKMLException("Failure loading KML file", e);
        }
        doc.getDocumentElement().normalize();
        return doc;
    }
}
