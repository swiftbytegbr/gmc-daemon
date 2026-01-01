# Certificate installation
# By https://github.com/Ch4r0ne/UnrealEngine_Dedicated_Server_Install_CA/tree/main

# Define the URLs for the Amazon Root CA
$amazonRootCAUrl = "https://www.amazontrust.com/repository/AmazonRootCA1.cer"
$certificateUrl = "http://crt.r2m02.amazontrust.com/r2m02.cer"

# Define the local paths for the certificates
$amazonRootCADirectory = "$env:TEMP\AmazonRootCA1.cer"
$targetDirectory = "$env:TEMP\r2m02.cer"

try {
    # Download the Amazon Root CA and the certificate
    Invoke-RestMethod -Uri $amazonRootCAUrl -OutFile $amazonRootCADirectory -ErrorAction Stop
    Invoke-RestMethod -Uri $certificateUrl -OutFile $targetDirectory -ErrorAction Stop

    # Import Amazon Root CA into the CA store of the current user
    Import-Certificate -FilePath $amazonRootCADirectory -CertStoreLocation Cert:\CurrentUser\CA -ErrorAction Stop
    Write-Output "Amazon Root CA has been installed in the CA store of the current user."

    # Import the certificate into the personal store of the current user
    Import-Certificate -FilePath $targetDirectory -CertStoreLocation Cert:\CurrentUser\My -ErrorAction Stop
    Write-Output "The certificate has been installed in the personal store of the current user."

    # Import the certificate into the machine store (LocalMachine)
    Import-Certificate -FilePath $targetDirectory -CertStoreLocation Cert:\LocalMachine\CA -ErrorAction Stop
    Write-Output "The certificate has been installed in the machine store (LocalMachine)."
}
catch {
    throw "An error occurred while installing the certificate: $_"
}
