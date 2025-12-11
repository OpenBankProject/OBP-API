# Keycloak Onboarding Guide

## Overview
Keycloak is an open-source identity and access management solution that provides production-grade OpenID Connect (OIDC) and OAuth 2.0 authentication services for the OBP-API. It serves as a centralized authentication provider that enables secure user authentication, authorization, and user management for banking applications.

## What is Keycloak?
Keycloak is a comprehensive identity provider that offers:
- **Single Sign-On (SSO)** capabilities across multiple applications
- **Identity Federation** with external identity providers (Google, Facebook, LDAP, etc.)
- **User Management** with role-based access control
- **Multi-factor Authentication (MFA)** support
- **Standards Compliance** with OAuth 2.0, OpenID Connect, and SAML 2.0

## Prerequisites for Onboarding

### System Requirements
- Docker or Java 11+ for running Keycloak
- Network access to Keycloak instance (default: `localhost:7787`)
- Administrative access to configure realms and clients

### OBP-API Configuration
Before integrating with Keycloak, ensure your OBP-API instance has the following properties configured:

# Enable OAuth2 login
`allow_oauth2_login=true`

# Keycloak-specific configuration
`oauth2.oidc_provider=keycloak`
`oauth2.keycloak.host=http://localhost:7787`
`oauth2.keycloak.realm=master`
`oauth2.keycloak.issuer=http://localhost:7787/realms/master`
`oauth2.jwk_set.url=http://localhost:7787/realms/master/protocol/openid-connect/certs`

## Step-by-Step Onboarding Process

### 1. Setting Up Keycloak Instance

#### Option A: Using Docker (Recommended for Development)

The OBP project provides a pre-configured Keycloak Docker image available at:
**Docker Hub**: https://hub.docker.com/r/openbankproject/obp-keycloak/tags

##### Inspect Available Tags
First, check available image tags:
`docker search openbankproject/obp-keycloak`

Common available tags:
- `main-themed`: Latest themed version with OBP branding
- `latest`: Standard latest version
- `dev`: Development version
- Version-specific tags (e.g., `21.1.2-themed`)

##### Pull and Inspect the Image
# Pull the OBP-themed Keycloak image
`docker pull openbankproject/obp-keycloak:main-themed`

# Inspect image details
`docker inspect openbankproject/obp-keycloak:main-themed`

# View image layers and size
`docker images | grep openbankproject/obp-keycloak`

# Check image history
`docker history openbankproject/obp-keycloak:main-themed`

##### Basic Container Setup
# Run Keycloak container with basic configuration
`docker run -p 7787:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \`
`  openbankproject/obp-keycloak:main-themed start-dev`

##### Advanced Container Setup with Persistent Data
# Create a volume for persistent data
`docker volume create keycloak_data`

# Run with persistent data and custom configuration
`docker run -d --name obp-keycloak \`
`  -p 7787:8080 \`
`  -e KEYCLOAK_ADMIN=admin \`
`  -e KEYCLOAK_ADMIN_PASSWORD=admin \`
`  -e KC_DB=h2-file \`
`  -e KC_DB_URL_PATH=/opt/keycloak/data/keycloak \`
`  -v keycloak_data:/opt/keycloak/data \`
`  openbankproject/obp-keycloak:main-themed start-dev`

##### Container Management Commands
# Check container status
`docker ps | grep obp-keycloak`

# View container logs
`docker logs obp-keycloak`

# Follow logs in real-time
`docker logs -f obp-keycloak`

# Stop the container
`docker stop obp-keycloak`

# Start existing container
`docker start obp-keycloak`

# Remove container (data will be preserved in volume)
`docker rm obp-keycloak`

##### Troubleshooting Container Issues
# Execute commands inside running container
`docker exec -it obp-keycloak bash`

# Check container resource usage
`docker stats obp-keycloak`

# Inspect container configuration
`docker inspect obp-keycloak`

##### Environment Variables and Configuration
The OBP Keycloak image supports these key environment variables:

**Admin Configuration:**
- `KEYCLOAK_ADMIN`: Admin username (default: admin)
- `KEYCLOAK_ADMIN_PASSWORD`: Admin password
- `KC_PROXY`: Proxy mode (edge, reencrypt, passthrough)

**Database Configuration:**
- `KC_DB`: Database type (h2-file, postgres, mysql, mariadb)
- `KC_DB_URL`: Database connection URL
- `KC_DB_USERNAME`: Database username
- `KC_DB_PASSWORD`: Database password

**Network Configuration:**
- `KC_HOSTNAME`: External hostname for Keycloak
- `KC_HTTP_PORT`: HTTP port (default: 8080)
- `KC_HTTPS_PORT`: HTTPS port (default: 8443)

**Example with External Database:**
`docker run -d --name obp-keycloak \`
`  -p 7787:8080 \`
`  -e KEYCLOAK_ADMIN=admin \`
`  -e KEYCLOAK_ADMIN_PASSWORD=securepassword \`
`  -e KC_DB=postgres \`
`  -e KC_DB_URL=jdbc:postgresql://localhost:5432/keycloak \`
`  -e KC_DB_USERNAME=keycloak \`
`  -e KC_DB_PASSWORD=keycloak_password \`
`  openbankproject/obp-keycloak:main-themed start-dev`

#### Option B: Manual Installation
1. Download Keycloak from [keycloak.org](https://www.keycloak.org/)
2. Extract and navigate to the Keycloak directory
3. Start Keycloak in development mode:
   `./bin/kc.sh start-dev --http-port=7787`

### 2. Initial Keycloak Configuration

#### Access Keycloak Admin Console
- Navigate to <a href="http://localhost:7787/admin" target="_blank">http://localhost:7787/admin</a>
- Login with admin credentials (admin/admin for Docker setup)

#### Create or Configure Realm
1. Select or create a realm (e.g., "obp" or use "master")
2. Configure realm settings:
   - **SSL required**: None (for development) or External requests (for production)
   - **User registration**: Enable if needed
   - **Login with email**: Enable for email-based authentication

### 3. Configure OBP Client Application

#### Create Client
1. Navigate to **Clients** → **Create Client**
2. Set **Client ID**: `obp-client` (or your preferred identifier)
3. Set **Client Type**: `OpenID Connect`
4. Enable **Client authentication**

#### Configure Client Settings
- **Root URL**: <a href="getServerUrl" target="_blank">your OBP-API URL</a>
- **Valid redirect URIs**: <a href="getServerUrl/auth/openid-connect/callback" target="_blank">callback URL</a>
- **Web origins**: <a href="getServerUrl" target="_blank">OBP-API URL</a>
- **Access Type**: `confidential`

#### Retrieve Client Credentials
1. Go to **Clients** → **obp-client** → **Credentials**
2. Copy the **Client Secret** for OBP-API configuration

### 4. Update OBP-API Configuration

Add the following to your OBP-API properties file:

# OpenID Connect Client Configuration
`openid_connect_1.button_text=Keycloak Login`
`openid_connect_1.client_id=obp-client`
`openid_connect_1.client_secret=YOUR_CLIENT_SECRET_HERE`
`openid_connect_1.callback_url=getServerUrl/auth/openid-connect/callback`
`openid_connect_1.endpoint.discovery=http://localhost:7787/realms/master/.well-known/openid-configuration`
`openid_connect_1.endpoint.authorization=http://localhost:7787/realms/master/protocol/openid-connect/auth`
`openid_connect_1.endpoint.userinfo=http://localhost:7787/realms/master/protocol/openid-connect/userinfo`
`openid_connect_1.endpoint.token=http://localhost:7787/realms/master/protocol/openid-connect/token`
`openid_connect_1.endpoint.jwks_uri=http://localhost:7787/realms/master/protocol/openid-connect/certs`
`openid_connect_1.access_type_offline=true`

### 5. User Management Setup

#### Create Test Users
1. Navigate to **Users** → **Add User**
2. Fill in user details:
   - **Username**: `testuser`
   - **Email**: `testuser@example.com`
   - **First Name** and **Last Name**
3. Set temporary password in **Credentials** tab
4. Assign appropriate roles if using role-based access control

#### Configure User Attributes (Optional)
Map additional user attributes that OBP-API might need:
- `email` (standard)
- `name` (standard)
- `preferred_username` (standard)
- Custom attributes as required by your banking application

### 6. Testing the Integration

#### Verify Configuration
1. Restart OBP-API after configuration changes
2. Navigate to <a href="getServerUrl" target="_blank">OBP-API login page</a>
3. Look for "Keycloak Login" button (or your configured button text)

#### Test Authentication Flow
1. Click the Keycloak login button
2. Should redirect to Keycloak login page
3. Login with test user credentials
4. Should redirect back to OBP-API with successful authentication
5. Verify user session and JWT token validity

## Production Considerations

### Security Best Practices
- **Use HTTPS** for all Keycloak and OBP-API communications
- **Strong passwords** for admin accounts
- **Regular updates** of Keycloak version
- **Backup** realm configurations and user data
- **Monitor** authentication logs for suspicious activity

### Scalability
- **Database**: Configure external database (PostgreSQL, MySQL) instead of H2
- **Clustering**: Set up Keycloak cluster for high availability
- **Load Balancing**: Use load balancer for multiple Keycloak instances

### Integration with Banking Systems
- **LDAP/Active Directory**: Integrate with existing user directories
- **Multi-factor Authentication**: Enable MFA for enhanced security
- **Compliance**: Ensure configuration meets banking regulatory requirements

## Troubleshooting Common Issues

### Authentication Failures
- **Check redirect URIs**: Ensure they match exactly in both Keycloak and OBP-API
- **Verify client credentials**: Confirm client ID and secret are correct
- **Check realm configuration**: Ensure realm name matches in all configurations

### JWT Token Issues
- **Issuer mismatch**: Verify `oauth2.keycloak.issuer` matches JWT `iss` claim
- **JWKS endpoint**: Confirm `oauth2.jwk_set.url` is accessible and returns valid keys
- **Token expiration**: Check token validity periods in Keycloak settings

### Network Connectivity
- **Firewall rules**: Ensure ports 7787 (Keycloak) and 8080 (OBP-API) are accessible
- **DNS resolution**: Verify hostnames resolve correctly
- **SSL certificates**: For HTTPS setups, ensure valid certificates

## Additional Resources
- <a href="https://www.keycloak.org/documentation" target="_blank">Keycloak Official Documentation</a>
- <a href="https://openid.net/connect/" target="_blank">OpenID Connect Specification</a>
- [OBP OAuth 2.0 Client Credentials Flow Manual](OAuth_2.0_Client_Credentials_Flow_Manual.md)
- [OBP OIDC Configuration Guide](../../../OBP_OIDC_Configuration_Guide.md)

## Support
For issues related to Keycloak integration with OBP-API:
1. Check the OBP-API logs for detailed error messages
2. Verify Keycloak server logs for authentication issues
3. Consult the OBP community forums or GitHub issues
4. Review the comprehensive troubleshooting section in the OBP documentation