#!groovy
def decidePipeline(Map configMap){
    application = configMap.get("application")
    switch(application) {
        case 'goEKS':
            goEKS(configMap)
            break
        default:
            error "Application is not recognised"
            break
    }
}