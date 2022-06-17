buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  doKubeDeploy = true
  buildNode = 'jenkins-agent-java11'

  doApiLint = true
  doApiDoc = true
  apiTypes = 'OAS'
  apiDirectories = 'src/main/resources/swagger.api'

  doDocker = {
    buildDocker {
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:8081/admin/health || exit 1'
    }
  }
}

