package ru.hse.spb

import java.io.*

@DslMarker
annotation class ElementMarker

@ElementMarker
interface Element {
    fun renderBegin(writer: Writer, indent: String)
    fun renderEnd(writer: Writer, indent: String) {}
    fun render(writer: Writer, indent: String) {
        renderBegin(writer, indent)
        renderEnd(writer, indent)
    }
}

interface AttributedElement : Element {
    fun renderAttributes() : String
}

class TextElement(private val text: String) : Element {
    override fun renderBegin(writer: Writer, indent: String) {
        writer.write("$indent$text\n")
    }
}

abstract class Command(private val name: String) : AttributedElement {
    var parameter: String = ""
    var attributes: List<String> = mutableListOf()

    override fun renderBegin(writer: Writer, indent: String) {
        writer.write("$indent\\$name${renderAttributes()}{$parameter}\n")
    }

    override fun renderAttributes() : String {
        if (attributes.isEmpty())
            return ""
        var rendered = ""
        rendered += "["
        var left = attributes.size
        for (attr in attributes) {
            rendered += attr
            left--
            if (left > 0)
                rendered += ", "
        }
        rendered += "]"
        return rendered
    }
}

abstract class Tag(protected val name: String) : AttributedElement {
    var attributes = hashMapOf<String, String>()

    override fun renderBegin(writer: Writer, indent: String) {
        writer.write("$indent\\begin{$name}${renderAttributes()}\n")
    }

    override fun renderAttributes() : String {
        if (attributes.isEmpty())
            return ""
        var rendered = ""
        rendered += "["
        var left = attributes.size
        for ((attr, value) in attributes) {
            rendered += "$attr=$value"
            left--
            if (left > 0)
                rendered += ", "
        }
        rendered += "]"
        return rendered
    }
}

abstract class TagWithText(name: String) : Tag(name) {
    protected val children = mutableListOf<Element>()

    protected fun <T: Element> initTag(tag: T, init: T.() -> Unit, vararg args: Pair<String, String>): T {
        tag.init()
        if (tag is Tag)
            tag.attributes = hashMapOf(*args)
        children.add(tag)
        return tag
    }

    operator fun String.unaryPlus() {
        children.add(TextElement(this))
    }

    override fun render(writer: Writer, indent: String) {
        renderBegin(writer, indent)
        for (child in children)
            child.render(writer, "$indent  ")
        renderEnd(writer, indent)
    }

    override fun renderEnd(writer: Writer, indent: String) {
        writer.write("$indent\\end{$name}\n")
    }
}

abstract class BodyTag(name: String) : TagWithText(name) {
    fun documentClass(parameter: String) {
        val documentClass = DocumentClass()
        documentClass.parameter = parameter
        children.add(documentClass)
    }

    fun usepackage(name: String, vararg attributes: String) {
        val texPackage = UsePackage()
        texPackage.parameter = name
        texPackage.attributes = attributes.asList()
        children.add(texPackage)
    }

    fun frame(vararg args: Pair<String, String>, frameTitle: String? = null, init: Frame.() -> Unit) {
        val frame = Frame()
        if (frameTitle != null) {
            val title = FrameTitle()
            title.parameter = frameTitle
            frame.children.add(title)
        }
        initTag(frame, init, *args)
    }

    fun itemize(vararg args: Pair<String, String>, init: Itemize.() -> Unit) = initTag(Itemize(), init, *args)

    fun enumerate(vararg args: Pair<String, String>, init: Enumerate.() -> Unit) = initTag(Enumerate(), init, *args)

    fun math(init: Math.() -> Unit) = initTag(Math(), init)

    fun flushleft(init: FlushLeft.() -> Unit) = initTag(FlushLeft(), init)

    fun flushright(init: FlushRight.() -> Unit) = initTag(FlushRight(), init)

    fun center(init: Center.() -> Unit) = initTag(Center(), init)

    fun customTag(vararg args: Pair<String, String>, name: String, init: CustomTag.() -> Unit) =
        initTag(CustomTag(name), init, *args)
}

abstract class ListTag(name: String) : BodyTag(name) {
    fun item(init: Item.() -> Unit) = initTag(Item(), init)
}

class Document : BodyTag("document") {
    fun toOutputStream(stream: OutputStream) {
        val writer = PrintWriter(stream)
        render(writer, "")
        writer.flush()
    }

    override fun toString(): String {
        val writer = StringWriter()
        render(writer, "")
        return writer.toString()
    }
}

class DocumentClass : Command("documentClass")
class UsePackage : Command("usepackage")
class Frame : BodyTag("frame")
class FrameTitle : Command("frametitle")
class Itemize : ListTag("itemize")
class Enumerate : ListTag("enumerate")
class Item : BodyTag("item") {
    override fun renderBegin(writer: Writer, indent: String) {
        writer.write("$indent\\item\n")
    }

    override fun renderEnd(writer: Writer, indent: String) {}
}
class Math : BodyTag("gather*")
class FlushLeft : BodyTag("flushleft")
class FlushRight : BodyTag("flushright")
class Center : BodyTag("center")
class CustomTag(name: String) : BodyTag(name)

fun document(init: Document.() -> Unit): Document {
    val document = Document()
    document.init()
    return document
}