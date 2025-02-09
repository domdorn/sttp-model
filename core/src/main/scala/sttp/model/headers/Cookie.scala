package sttp.model.headers

import sttp.model.Header
import sttp.model.headers.Cookie.SameSite
import sttp.model.internal.Rfc2616.validateToken
import sttp.model.internal.Validate._
import sttp.model.internal.{Rfc2616, Validate}

import java.time.Instant
import scala.util.{Failure, Success, Try}

/** A cookie name-value pair.
  *
  * The `name` and `value` should be already encoded (if necessary), as when serialised, they end up unmodified in the
  * header.
  */
case class Cookie(name: String, value: String) {

  /** @return
    *   Representation of the cookie as in a header value, in the format: `[name]=[value]`.
    */
  override def toString: String = s"$name=$value"
}

/** For a description of the behavior of `apply`, `parse`, `safeApply` and `unsafeApply` methods, see [[sttp.model]].
  */
object Cookie {
  // see: https://stackoverflow.com/questions/1969232/allowed-characters-in-cookies/1969339
  private val AllowedValueCharacters = s"[^${Rfc2616.CTL}]*".r

  private[model] def validateName(name: String): Option[String] = validateToken("Cookie name", name)

  private[model] def validateValue(value: String): Option[String] =
    if (AllowedValueCharacters.unapplySeq(value).isEmpty) {
      Some("Cookie value can not contain control characters")
    } else None

  /** @throws IllegalArgumentException
    *   If the cookie name or value contain illegal characters.
    */
  def unsafeApply(name: String, value: String): Cookie = safeApply(name, value).getOrThrow

  def safeApply(name: String, value: String): Either[String, Cookie] = {
    Validate.all(validateName(name), validateValue(value))(new Cookie(name, value))
  }

  /** Parse the cookie, represented as a header value (in the format: `[name]=[value]`).
    */
  def parse(s: String): Either[String, List[Cookie]] = {
    val cs = s.split(";").toList.map { ss =>
      (ss.split("=", 2).map(_.trim): @unchecked) match {
        case Array(v1)     => Cookie.safeApply(v1, "")
        case Array(v1, v2) => Cookie.safeApply(v1, v2)
      }
    }

    Validate.sequence(cs)
  }

  def unsafeParse(s: String): List[Cookie] = parse(s).getOrThrow

  /** @return
    *   Representation of the cookies as in a header value, in the format: `[name]=[value]; [name]=[value]; ...`.
    */
  def toString(cs: List[Cookie]): String = cs.map(_.toString).mkString("; ")

  sealed trait SameSite
  object SameSite {
    case object Lax extends SameSite { override def toString = "Lax" }
    case object Strict extends SameSite { override def toString = "Strict" }
    case object None extends SameSite { override def toString = "None" }
  }
}

case class CookieValueWithMeta(
    value: String,
    expires: Option[Instant],
    maxAge: Option[Long],
    domain: Option[String],
    path: Option[String],
    secure: Boolean,
    httpOnly: Boolean,
    sameSite: Option[SameSite],
    otherDirectives: Map[String, Option[String]]
)

object CookieValueWithMeta {
  private val AllowedDirectiveValueCharacters = s"""[^;${Rfc2616.CTL}]*""".r

  private[model] def validateDirectiveValue(directiveName: String, value: String): Option[String] = {
    if (AllowedDirectiveValueCharacters.unapplySeq(value).isEmpty) {
      Some(s"Value of directive $directiveName name can contain any characters except ; and control characters")
    } else None
  }

  def unsafeApply(
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
      sameSite: Option[SameSite] = None,
      otherDirectives: Map[String, Option[String]] = Map.empty
  ): CookieValueWithMeta =
    safeApply(value, expires, maxAge, domain, path, secure, httpOnly, sameSite, otherDirectives).getOrThrow

  def safeApply(
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
      sameSite: Option[SameSite] = None,
      otherDirectives: Map[String, Option[String]] = Map.empty
  ): Either[String, CookieValueWithMeta] = {
    Validate.all(
      Cookie.validateValue(value),
      path.flatMap(validateDirectiveValue("path", _)),
      domain.flatMap(validateDirectiveValue("domain", _))
    )(apply(value, expires, maxAge, domain, path, secure, httpOnly, sameSite, otherDirectives))
  }
}

/** A cookie name-value pair with directives.
  *
  * All `String` values should be already encoded (if necessary), as when serialised, they end up unmodified in the
  * header.
  */
case class CookieWithMeta(
    name: String,
    valueWithMeta: CookieValueWithMeta
) {
  def value: String = valueWithMeta.value
  def expires: Option[Instant] = valueWithMeta.expires
  def maxAge: Option[Long] = valueWithMeta.maxAge
  def domain: Option[String] = valueWithMeta.domain
  def path: Option[String] = valueWithMeta.path
  def secure: Boolean = valueWithMeta.secure
  def httpOnly: Boolean = valueWithMeta.httpOnly
  def sameSite: Option[SameSite] = valueWithMeta.sameSite
  def otherDirectives: Map[String, Option[String]] = valueWithMeta.otherDirectives

  def value(v: String): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(value = v))
  def expires(v: Option[Instant]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(expires = v))
  def maxAge(v: Option[Long]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(maxAge = v))
  def domain(v: Option[String]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(domain = v))
  def path(v: Option[String]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(path = v))
  def secure(v: Boolean): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(secure = v))
  def httpOnly(v: Boolean): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(httpOnly = v))
  def sameSite(s: Option[SameSite]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(sameSite = s))
  def otherDirective(v: (String, Option[String])): CookieWithMeta =
    copy(valueWithMeta = valueWithMeta.copy(otherDirectives = otherDirectives + v))

  /** @return
    *   Representation of the cookie as in a header value, in the format: `[name]=[value]; [directive]=[value]; ...`.
    */
  override def toString: String = {
    val components = List(
      Some(s"$name=$value"),
      expires.map(e => s"Expires=${Header.toHttpDateString(e)}"),
      maxAge.map(a => s"Max-Age=$a"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=$p"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=$s")
    ) ++ otherDirectives.map {
      case (k, Some(v)) => Some(s"$k=$v")
      case (k, None)    => Some(k)
    }

    components.flatten.mkString("; ")
  }
}

object CookieWithMeta {
  def unsafeApply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
      sameSite: Option[SameSite] = None,
      otherDirectives: Map[String, Option[String]] = Map.empty
  ): CookieWithMeta =
    safeApply(name, value, expires, maxAge, domain, path, secure, httpOnly, sameSite, otherDirectives).getOrThrow

  def safeApply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
      sameSite: Option[SameSite] = None,
      otherDirectives: Map[String, Option[String]] = Map.empty
  ): Either[String, CookieWithMeta] = {
    Cookie.validateName(name) match {
      case Some(e) => Left(e)
      case None =>
        CookieValueWithMeta
          .safeApply(value, expires, maxAge, domain, path, secure, httpOnly, sameSite, otherDirectives)
          .right
          .map { v =>
            apply(name, v)
          }
    }
  }

  def apply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
      sameSite: Option[SameSite] = None,
      otherDirectives: Map[String, Option[String]] = Map.empty
  ): CookieWithMeta =
    apply(
      name,
      CookieValueWithMeta(value, expires, maxAge, domain, path, secure, httpOnly, sameSite, otherDirectives)
    )

  // https://tools.ietf.org/html/rfc6265#section-4.1.1
  /** Parse the cookie, represented as a header value (in the format: `[name]=[value]; [directive]=[value]; ...`).
    */
  def parse(s: String): Either[String, CookieWithMeta] = {
    def splitkv(kv: String): (String, Option[String]) =
      (kv.split("=", 2).map(_.trim): @unchecked) match {
        case Array(v1)     => (v1, None)
        case Array(v1, v2) => (v1, Some(v2))
      }

    val components = s.split(";").map(_.trim)
    val (first, other) = (components.head, components.tail)
    val (name, value) = splitkv(first)
    var result: Either[String, CookieWithMeta] = Right(CookieWithMeta.apply(name, value.getOrElse("")))
    other.map(splitkv).map(t => (t._1, t._2)).foreach {
      case (ci"expires", Some(v)) =>
        Header.parseHttpDate(v) match {
          case Right(expires) => result = result.right.map(_.expires(Some(expires)))
          case Left(_) => result = Left(s"Expires cookie directive is not a valid RFC1123 or RFC850 datetime: $v")
        }
      case (ci"max-age", Some(v)) =>
        Try(v.toLong) match {
          case Success(maxAge) => result = result.right.map(_.maxAge(Some(maxAge)))
          case Failure(_)      => result = Left(s"Max-Age cookie directive is not a number: $v")
        }
      case (ci"domain", v)   => result = result.right.map(_.domain(Some(v.getOrElse(""))))
      case (ci"path", v)     => result = result.right.map(_.path(Some(v.getOrElse(""))))
      case (ci"secure", _)   => result = result.right.map(_.secure(true))
      case (ci"httponly", _) => result = result.right.map(_.httpOnly(true))
      case (ci"samesite", Some(v)) =>
        v.trim match {
          case ci"lax"    => result = result.right.map(_.sameSite(Some(SameSite.Lax)))
          case ci"strict" => result = result.right.map(_.sameSite(Some(SameSite.Strict)))
          case ci"none"   => result = result.right.map(_.sameSite(Some(SameSite.None)))
          case _          => result = Left(s"Same-Site cookie directive is not an allowed value: $v")
        }
      case (k, v) => result = result.right.map(_.otherDirective((k, v)))
    }

    result
  }

  def unsafeParse(s: String): CookieWithMeta = parse(s).getOrThrow

  private implicit class StringInterpolations(sc: StringContext) {
    class CaseInsensitiveStringMatcher {
      def unapply(other: String): Boolean = sc.parts.mkString.equalsIgnoreCase(other)
    }
    def ci: CaseInsensitiveStringMatcher = new CaseInsensitiveStringMatcher
  }
}
