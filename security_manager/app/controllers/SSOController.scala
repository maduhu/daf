package controllers

import javax.inject.Inject

import it.gov.daf.securitymanager.service.utilities.{ConfigReader, WebServiceUtil}
import it.gov.daf.sso.LoginClientLocal
import it.gov.daf.sso.common.{CacheWrapper, LoginInfo}
import play.api.inject.ConfigurationProvider
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller, Cookie}


class SSOController @Inject()(ws: WSClient, config: ConfigurationProvider) extends Controller {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext


  val cacheWrapper = CacheWrapper.init(ConfigReader.cookieExpiration,ConfigReader.tokenExpiration)

  //----------------SECURED API---------------------------------------

  // servono le credenziali da BA
  def register = Action { implicit request =>

    val credentials = WebServiceUtil.readCredentialFromRequest(request)

    if( !credentials._2.isEmpty ) {
      cacheWrapper.putCredentials(credentials._1.get, credentials._2.get)
      Ok("Success")
    }else
      InternalServerError("Basic Authentication required")

  }


  // serve token JWT o BA
  def retriveCookie(appName:String) = Action.async { implicit request =>

    val username = WebServiceUtil.readCredentialFromRequest(request)._1.get

    val loginInfo = new LoginInfo(
                                  username,
                                  cacheWrapper.getPwd(username).get,
                                  appName)


    //println("xxxxx:"+loginInfo.password)

    LoginClientLocal.instance.login(loginInfo, ws).map{ cookie =>
      val json=s"""{"result":"$cookie"}"""
      Ok(json)
    }

  }


  // serve token JWT o BA
  def login(appName:String) = Action.async { implicit request =>

    val username = WebServiceUtil.readCredentialFromRequest(request)._1.get

    val loginInfo = new LoginInfo(
                                  username,
                                  cacheWrapper.getPwd(username).get,
                                  appName)

    LoginClientLocal.instance.login(loginInfo, ws).map{ cookie =>
      val cookieDetail = cookie.split("=")
      val cookieWeb = Cookie( cookieDetail(0),cookieDetail(1), None, "/", None)
      Ok("Success").withCookies( cookieWeb )
    }

  }

  // serve token JWT
  def test = Action { implicit request =>

    val username = WebServiceUtil.readCredentialFromRequest(request)._1.get

    if( cacheWrapper.getPwd(username).isEmpty ) {
      Unauthorized("JWT expired")
    }else
      Ok("Success")

  }


  //-----------------UNSECURED API-----------------------------------------

  def registerInternal(username:String,password:String) = Action { implicit request =>

    if( username!=null &&  password != null) {
      cacheWrapper.putCredentials(username, password)
      Ok("Success")
    }else
      NotAcceptable("Username and password required")

  }


  def retriveCookieInternal(username:String,appName:String) = Action.async { implicit request =>

    val loginInfo = new LoginInfo(
                                  username,
                                  cacheWrapper.getPwd(username).get,
                                  appName)

    LoginClientLocal.instance.login(loginInfo, ws).map{ cookie => Ok(cookie) }

  }


}
