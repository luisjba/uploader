package com.luisjba.grails.plugins.uploader

import java.awt.Image as AWTImage 
import java.awt.image.BufferedImage      
import javax.swing.ImageIcon 
import javax.imageio.ImageIO as IIO  
import java.awt.Graphics2D
import grails.util.Environment
import grails.converters.XML
import grails.converters.JSON

class UploaderController {
	static allowedMethods = [index: ['GET', 'POST']]
	def index() {
		//The var to hols the Uploaded File Instance on a Domain class
		def uploadedFileDomainInstance
		//uploader config
		def uploaderConfig = grailsApplication.config.uploader
		//upload type
		def uploadType = params.remove("uploadType")
		if(!uploadType){
			response.status = 500
			render "missing uploadType param"
			return
		}	
		//Base path by environment
		def basePath = uploaderConfig.basePath
		switch (Environment.current) {
		    case Environment.DEVELOPMENT:
		        basePath = uploaderConfig?.environments["development"]?.basePath?:basePath
		        break
		    case Environment.PRODUCTION:
		        basePath = uploaderConfig?.environments["production"]?.basePath?:basePath
		        break
		}
		basePath = basePath+(basePath.endsWith('/')?"":"/")		
		//config handler
		def config = uploaderConfig.types[uploadType]
		if(!config){
			response.status = 500
			render message(code: config.messages?.missingTypeConfiguration?:uploaderConfig.messages.missingTypeConfiguration, args:[uploadType], default:"Missing configuration for '${uploadType}' Upload Type. Was not found in the configuration file")
			return
		}
		//check if is configured the event to check Authorization 
		if(config.hasAuthorization && !config.hasAuthorization(params)){
			response.status = 403
			render message(code: config.messages?.unAuthorizedAccess?:uploaderConfig.messages.unAuthorizedAccess, default:"You don't have Authorized Access")
			return
		}
		//relative Path  to save file
		def relativePath = config.path.substring(config.path.startsWith("/")?1:0)+(config.path.endsWith("/")?"":"/")
		//request file
		def file = null
		try{file = request.getFile(config.paramName?:"file")}catch(Exception er){}
		//response to render when deals with request format
		def responseReturn = [success:false]	
		/**************************
			check if file exists
		**************************/
		if (!file || file.size == 0) {
			if(!request.xhr){//Check if the return ues is not by Ajax
				responseReturn.message = message(code: config.messages?.noFile?:uploaderConfig.messages.noFile, default:"No file was sent, please select the file and try again.")
				flash.message = responseReturn.message
			}
		}else{
			/***********************
				check extensions
			************************/
			def fileExtension = file.originalFilename.substring(file.originalFilename.lastIndexOf('.')+1)
			if (!config.allowedExtensions[0].equals("*") && !config.allowedExtensions.contains(fileExtension)) {
				responseReturn.message = message(code: config.messages?.unauthorizedExtension?:uploaderConfig.messages.unauthorizedExtension, args:[fileExtension,config.allowedExtensions], default:"The file you sent has an unauthorized extension (${fileExtension}). Allowed extensions for this upload are ${config.allowedExtensions}")
				flash.message = responseReturn.message
			}else{
				/*********************
					check file size
				**********************/
				if (config.maxSize && (file.size/1024) > ((int)(config.maxSize/1024)) ){
					responseReturn.message = message(code: config.messages?.fileBiggerThanAllowed?:uploaderConfig.messages.fileBiggerThanAllowed, args:[((int)(config.maxSize/1024))], default:"Sent file is bigger than allowed. Max file size is ${((int)(config.maxSize/1024))} kb")
					flash.message = responseReturn.message
				}else{
					AWTImage ai = null
					int width, height 
					double aspectRatioNew, aspectRatioDefault 
					def matCtx = new java.math.MathContext(2)//to roun the values to 2 digits
					try{ 
						ai = new ImageIcon(file.getBytes()).image
						width = ai.getWidth( null ) //get width of image uploaded
			    		height = ai.getHeight( null ) //get height of image uploaded
			    		aspectRatioNew = (width/height).round(matCtx)// get aspect ratio of image uploaded and round to two digits 
			    		aspectRatioDefault = (config.width/config.height).round(matCtx)// get aspect ratio of default config and round to two digits
						
					}catch(Exception e){}
					if(config.width && config.height //check if have the width and height properties to check 
						&& (!ai //check if is corrup file
							|| (aspectRatioNew != aspectRatioDefault || config.width > width || config.height > height)//check image resoluton
							)
					    ){
						if(!ai){
							responseReturn.message = message(code: config.messages?.corruptFile?:uploaderConfig.messages.corruptFile, default:"Sent file is corrupt")
							flash.message = responseReturn.message
						}else{
							responseReturn.message = message(code: config.messages?.fileResolutionDifferentAllowed?:uploaderConfig.messages.fileResolutionDifferentAllowed, args:[width, height, aspectRatioNew, config.width, config.height, aspectRatioDefault], default:"The image resolution(${width}x${height} with aspect ratio ${aspectRatioNew}) is not allowed. The allowed resolution is ${config.width}x${config.height} px. or  aspect ratio ${aspectRatioDefault}")
							flash.message = responseReturn.message
						}
					}else{
						def currentTime = System.currentTimeMillis()
						//sets new path
						relativePath = relativePath+currentTime+"/"
						if (!new File(basePath+relativePath).mkdirs()){
							responseReturn.message = message(code: config.messages?.dirNotCreated?:uploaderConfig.messages.dirNotCreated, args:[basePath+relativePath], default:"The directories for the file could not be created")
							flash.message = responseReturn.message
						}else{
							relativePath=relativePath+file.originalFilename
							//move file
							file.transferTo(new File(basePath+relativePath))
							uploadedFileDomainInstance = config.domainClass.newInstance(uploadType:uploadType, name:file.originalFilename,replace(fileExtension,""), size:file.size, extension: fileExtension, basePath:basePath, path:relativePath, dateUploaded:new Date(currentTime), downloads:0)
							//if  the afterNewDomainClass event is fired, it must retun true to continue to save changes, otherwise will not execute the save code
							if(config.afterNewDomainClass && !config.afterNewDomainClass([domainClassInstance:uploadedFileDomainInstance, requestParams:params, responseMap:responseReturn])){
								if(!uploadedFileDomainInstance.validate()){
									responseReturn.errors = uploadedFileDomainInstance.errors.allErrors
									new File(basePath+relativePath).delete()
								}
							}else{
								if(!uploadedFileDomainInstance.save(flush:true)){
									responseReturn.errors = uploadedFileDomainInstance.errors.allErrors
									
								}else{
									responseReturn.success = true
					                responseReturn.message = message(code: "default.created.message", args: [message(code: "${uploadedFileDomainInstance.class.logicalPropertyName}.label", default: "${uploadedFileDomainInstance.class.naturalName}"), uploadedFileDomainInstance])
					                flash.message = responseReturn.message
								}
							} 
						}
					}	
				}
			}
		}
		//Content Negotiation with the format Request Parameter
		def model = [uploadedFileDomainInstance: uploadedFileDomainInstance?:config.domainClass.newInstance(params)]
		request.withFormat {
			json {render responseReturn as JSON}
		    xml { render responseReturn as XML }
			html model
		}
		withFormat {
		    json {render responseReturn as JSON}
		    xml { render responseReturn as XML }
			html {
				if(request.method=="GET"){// rendering the corect view, andh check if the request is made by Ajax to Try to render the ajaxView on the configuration file if exists
					if(request.xhr){flash.message = null}//delete the flash.message if the request is made by Ajax
					render view:request.xhr && config.ajaxView ? config.ajaxView:config.view, model: model
				}else{//the method is by POST
					if(responseReturn.success){
						def redirectParams = config.successRedirect?:[:]// get the redirect configuration, if not exist an empty map is created
						if(params.successController){ redirectParams.controller=params.remove("successController") }
						if(params.successAction){ redirectParams.action=params.remove("successAction") }
						if(params.successId){ redirectParams.id=params.remove("successId") }
						redirectParams.params = params
						if(config.onSuccess){ config.onSuccess([redirectParams:redirectParams, domainClassInstance:uploadedFileDomainInstance]) }
						redirect redirectParams
					}else{
						render view:config.view, model: model
					}
				}
			}
		}
	}
    
    def show(){
		//uploader config
		def uploaderConfig = grailsApplication.config.uploader
		//upload type
		def uploadType = params.remove("uploadType")
		if(!uploadType){
			response.status = 500
			render "missing uploadType param"
			return
		}
		def config = uploaderConfig.types[uploadType]
		if(!config){
			response.status = 500
			render message(code: config.messages?.missingTypeConfiguration?:uploaderConfig.messages.missingTypeConfiguration, args:[uploadType], default:"Missing configuration for '${uploadType}' Upload Type. Was not found in the configuration file")
			return
		}
		//check if is configured the event to check Authorization 
		if(config.hasAuthorization && !config.hasAuthorization(params)){
			response.status = 403
			render message(code: config.messages?.unAuthorizedAccess?:uploaderConfig.messages.unAuthorizedAccess, default:"You don't have Authorized Access")
			return
		}
		//The var to hols the Uploaded File Instance on a Domain class
		def uploadedFileDomainInstance = config.domainClass.get(params.id)
		if(!uploadedFileDomainInstance){
			response.status = 404
			render message(code: config.messages?.fileNotFound?:uploaderConfig.messages.fileNotFound, default:"File NOT Found")
			return
		}else{
			def file = new File(uploadedFileDomainInstance.basePath+(uploadedFileDomainInstance.basePath.endsWith("/")?"":"/")+uploadedFileDomainInstance.path.substring(uploadedFileDomainInstance.path.startsWith("/")?1:0))
			if (file.exists()) {
				response.setContentType("image/"+uploadedFileDomainInstance.extension)
		        response.setContentLength(uploadedFileDomainInstance.size.toInteger())
		        OutputStream out = response.getOutputStream();
				out.write(file.bytes)
		        out.close();
			}else{
				response.status = 404
				render message(code: config.messages?.fileNotFound?:uploaderConfig.messages.fileNotFound, default:"File NOT Found")
				return
			}
		}
	}
}
