

buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  publishAPI = true
  runLintRamlCop = true
  doKubeDeploy = true

  doDocker = {
    buildJavaDocker {
      publishMaster = true
      healthChk = true
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}

