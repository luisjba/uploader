package com.luisjba.grails.plugins.uploader

import org.codehaus.groovy.grails.web.taglib.exceptions.GrailsTagException

class UploaderTagLib {
	static namespace = 'uploader'
	
	def form = { attrs ->
		//checking required fields
		if (!attrs.uploadType) {
			def errorMsg = "'uploadType' attribute not found in uploader form tag."
			log.error (errorMsg)
			throw new GrailsTagException(errorMsg)
		}
		def config = grailsApplication.config.uploader.types[attrs.uploadType]
		if(!config || config.empty){
			def errorMsg = "Missing configuration for '${attrs.uploadType}' Upload Type. Was not found in the configuration file"
			log.error (errorMsg)
			throw new GrailsTagException(errorMsg)
		}
		def formParams = attrs.remove("params")?:[:]
		formParams.uploadType = attrs.remove("uploadType")
		if(attrs.successController){
			formParams.successController = attrs.remove("successController")
		}
		if(attrs.successAction){
			formParams.successAction = attrs.remove("successAction")
		}
		if(attrs.successId){
			formParams.successId = attrs.remove("successId")
		}
		//form build
		StringBuilder sb = new StringBuilder()
		sb.append g.uploadForm([controller: "uploader", action: "index", id:attrs.id, params:formParams], "<input type='file' name='${config.paramName?:'file'}' />")
		
		out << sb.toString()
	}
}
