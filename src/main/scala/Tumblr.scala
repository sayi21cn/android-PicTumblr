package net.tokyoenvious.droid.pictumblr

import android.util.Log
import scala.xml.XML
import java.net.URL
import java.net.URLEncoder

// 2.8 API
class SeqExtra[A] (seq : Seq[A]) {
    def reduceLeftOption[B >: A](op: (B, A) => B): Option[B] =
        if (seq.isEmpty) None else Some(seq.reduceLeft(op))
}

class Tumblr (email : String, password : String) {
    implicit def seq2extra[A] (seq: Seq[A]) : SeqExtra[A]
        = new SeqExtra[A](seq)

    val API_ROOT = "http://www.tumblr.com/api/"
    val maxWidth = 500

    abstract class Post(id : Long, reblogKey : String)
    case class PhotoPost (id : Long, reblogKey : String, urlWithSlug : String, photoUrl : String, photoLinkUrl : Option[String], photoCaption : String)
            extends Post(id, reblogKey)

    // とりあえずタイトルを返す
    def authenticate () : Option[String] = {
        try {
            // API lv7 でえらる…… (SDK のバグ？)
            return makeApiRequest("authenticate").elements.next.attribute("title").map(_.text)
        } catch {
            case e => {
                Log.d("Tumblr.authenticate", e.toString)
                return None
            }
        }
    }

    def dashboard (params : (Symbol, String)*) : Seq[Post] = {
        for {
            postElem <- ( makeApiRequest("dashboard", params ++ Seq('type -> "photo") : _*) \ "posts" \ "post" )
            photoUrl <- (postElem \ "photo-url").reduceLeftOption {
                (node1, node2) => {
                    val w1 = getPhotoUrlNodeMaxWidth(node1)
                    val w2 = getPhotoUrlNodeMaxWidth(node2)
                    if (w1 > w2) node1 else node2
                }
            } map { _.text }
            id           <- ( postElem \ "@id" ).firstOption map { _.text.toLong }
            reblogKey    <- ( postElem \ "@reblog-key" ).firstOption map { _.text }
            photoCaption <- ( postElem \ "photo-caption" ).firstOption map { _.text }
            urlWithSlug  <- ( postElem \ "@url-with-slug" ).firstOption map { _.text }
            photoLinkUrl  = ( postElem \ "photo-link-url" ).firstOption map { _.text }
        } yield {
            new PhotoPost (id, reblogKey, urlWithSlug, photoUrl, photoLinkUrl, photoCaption)
        }
    }

    private def getPhotoUrlNodeMaxWidth (node : scala.xml.Node) : Int =
        ( node \ "@max-width" ).firstOption map { _.text.toInt } filter { _ < maxWidth } getOrElse(0)

    private def makeApiRequest (function : String, params : (Symbol, String)*) : scala.xml.Elem = {
        Log.d("Tumblr.makeApiRequest", "Requesting " + API_ROOT + function)

        var url = new URL(API_ROOT + function)
        val http = url.openConnection.asInstanceOf[java.net.HttpURLConnection]
        http.setRequestMethod("POST")
        http.setDoOutput(true)
        http.connect

        val writer = new java.io.OutputStreamWriter(http.getOutputStream)
        writer.write(mkPostString(params : _*))
        writer.close

        return XML.load(http.getInputStream)
    }

    private def mkPostString (params : (Symbol, String)*) : String = {
        ( Map('email -> email, 'password -> password) ++ params )
            .map { case (key, value) => key.name + "=" + URLEncoder.encode(value) }
                .mkString("&")
    }
}
