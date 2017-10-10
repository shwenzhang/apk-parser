package tinker.net.dongliu.apk.parser.parser;

import tinker.net.dongliu.apk.parser.struct.xml.Attribute;
import tinker.net.dongliu.apk.parser.struct.xml.XmlCData;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNodeEndTag;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNodeStartTag;

/**
 * callback interface for parse binary xml file.
 *
 * @author dongliu
 */
public interface XmlStreamer {

    void onStartTag(XmlNodeStartTag xmlNodeStartTag);

    void onAttribute(Attribute attribute);

    void onEndTag(XmlNodeEndTag xmlNodeEndTag);

    void onCData(XmlCData xmlCData);

    void onNamespaceStart(XmlNamespaceStartTag tag);

    void onNamespaceEnd(XmlNamespaceEndTag tag);
}
