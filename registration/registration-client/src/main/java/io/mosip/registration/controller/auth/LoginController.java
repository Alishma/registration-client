package io.mosip.registration.controller.auth;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.LoginMode;
import io.mosip.registration.constants.ProcessNames;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.controller.FXUtils;
import io.mosip.registration.controller.device.Streamer;
import io.mosip.registration.controller.reg.HeaderController;
import io.mosip.registration.controller.reg.Validations;
import io.mosip.registration.dto.AuthenticationValidatorDTO;
import io.mosip.registration.dto.ErrorResponseDTO;
import io.mosip.registration.dto.LoginUserDTO;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.UserDTO;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.enums.Role;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegBaseUncheckedException;
import io.mosip.registration.scheduler.SchedulerUtil;
import io.mosip.registration.service.bio.BioService;
import io.mosip.registration.service.login.LoginService;
import io.mosip.registration.service.operator.UserMachineMappingService;
import io.mosip.registration.update.SoftwareUpdateHandler;
import io.mosip.registration.util.common.OTPManager;
import io.mosip.registration.util.common.PageFlow;
import io.mosip.registration.util.restclient.AuthTokenUtilService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Class for loading Login screen with Username and password
 * 
 * @author Sravya Surampalli
 * @since 1.0.0
 *
 */
@Controller
public class LoginController extends BaseController implements Initializable {
	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(LoginController.class);

	@FXML
	private BorderPane loginScreen;

	@FXML
	private AnchorPane userIdPane;

	@FXML
	private AnchorPane credentialsPane;

	@FXML
	private AnchorPane otpPane;

	@FXML
	private AnchorPane fingerprintPane;

	@FXML
	private AnchorPane irisPane;

	@FXML
	private AnchorPane facePane;

	@FXML
	private AnchorPane errorPane;

	@FXML
	private TextField userId;

	@FXML
	private TextField password;

	@FXML
	private TextField otp;

	@FXML
	private Button submit;

	@FXML
	private Button otpSubmit;

	@FXML
	private Button getOTP;

	@FXML
	private Button resend;

	@FXML
	private Label otpValidity;

	@FXML
	private Hyperlink forgotUsrnme;

	@FXML
	private Hyperlink forgotPword;
	
	@FXML
	private AnchorPane contentPane;

	@FXML
	private ProgressIndicator progressIndicator;

	@FXML
	private ProgressIndicator fpProgressIndicator;

	@FXML
	private ProgressIndicator irisProgressIndicator;

	@FXML
	private ProgressIndicator faceProgressIndicator;

	@FXML
	private ProgressIndicator passwordProgressIndicator;

	@FXML
	private Label versionValueLabel;

	@FXML
	private ComboBox<GenericDto> appLanguage;

	@FXML
	private ImageView faceImage;

	@FXML
	private ImageView mosipLogoImageView;

	@FXML
	private ImageView fingerprintImageView;

	@FXML
	private ImageView irisImageView;

	@FXML
	private AnchorPane headingPane;

	@Autowired
	private LoginService loginService;

	@Autowired
	private OTPManager otpGenerator;

	@Autowired
	private SchedulerUtil schedulerUtil;

	@Autowired
	private Validations validations;

	@Autowired
	private PageFlow pageFlow;

	@Autowired
	private Streamer streamer;

	@Autowired
	private BioService bioService;

	@Autowired
	private AuthTokenUtilService authTokenUtilService;

	@Autowired
	private UserMachineMappingService machineMappingService;

	@Autowired
	private HeaderController headerController;

	@Autowired
	private SoftwareUpdateHandler softwareUpdateHandler;

	private BorderPane loginRoot;
	private Service<List<String>> taskService;
	private List<String> loginList = new ArrayList<>();
	private boolean isUserNewToMachine;
	private boolean hasUpdate;
	private String userName;

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {

		versionValueLabel.setText(softwareUpdateHandler.getCurrentVersion());
		
		setImage(mosipLogoImageView	, RegistrationConstants.MOSIP_LOGON_FINAL_IMG);
		setImage(fingerprintImageView	, RegistrationConstants.FINGERPRINT_SCAN_IMG);
		setImage(faceImage	, RegistrationConstants.FACE_SCAN_IMG);
		setImage(irisImageView	, RegistrationConstants.EYE_SCAN_IMG);
		

		new Thread(() -> {

			try {
				if (serviceDelegateUtil.isNetworkAvailable()) {

					// Check for updates
					hasUpdate = headerController.hasUpdate();

				}
			} catch (RuntimeException runtimeException) {
				LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
						runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
			}
		}).start();

		try {
			List<GenericDto> languages = getConfiguredLanguagesForLogin();
			appLanguage.getItems().addAll(languages);
			
			appLanguage.setConverter(FXUtils.getInstance().getStringConverterForComboBox());
			Optional<GenericDto> selectedLang = languages.stream().filter( l ->
					ApplicationContext.getInstance().getApplicationLanguage() != null &&
							ApplicationContext.getInstance().getApplicationLanguage().equals(l.getCode())).findFirst();

			if(!selectedLang.isPresent()) {
				LOGGER.info("No Language is set in applicationContext, Default selection : {}", languages.get(0));
				appLanguage.getSelectionModel().select(languages.get(0));
			} else {
				appLanguage.getSelectionModel().select(selectedLang.get());
			}

			appLanguage.setVisible(languages.size() == 1 ? false : true);
			appLanguage.setManaged(languages.size() == 1 ? false : true);

			appLanguage.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
				if (newValue != null &&
						!ApplicationContext.getInstance().getApplicationLanguage().equals(newValue.getCode())) {
					userName = userId.getText();
					ApplicationContext.getInstance().setApplicationLanguage(newValue.getCode());
					ResourceBundle messageBundle = io.mosip.registration.context.ApplicationContext.getBundle(
						io.mosip.registration.context.ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES);
					Validations.setResourceBundle(messageBundle);
					RegistrationUIConstants.setBundle(messageBundle);
					loadInitialScreen(getStage());
				}
			});

			stopTimer();
			password.textProperty().addListener((obsValue, oldValue, newValue) -> {
				String passwordLength = getValueFromApplicationContext(RegistrationConstants.PWORD_LENGTH);
				if (passwordLength != null && passwordLength.matches("\\d+")
						&& (newValue.length() > Integer.parseInt(passwordLength))) {
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PWORD_LENGTH));
				}
			});

			String otpExpiryTime = getValueFromApplicationContext(RegistrationConstants.OTP_EXPIRY_TIME);
			int otpExpirySeconds = 0;
			if (otpExpiryTime != null) {
				otpExpirySeconds = Integer
						.parseInt((getValueFromApplicationContext(RegistrationConstants.OTP_EXPIRY_TIME)).trim());
			}
			int minutes = otpExpirySeconds / 60;
			String seconds = String.valueOf(otpExpirySeconds % 60);
			seconds = seconds.length() < 2 ? "0" + seconds : seconds;
			otpValidity.setText(RegistrationUIConstants.getMessageLanguageSpecific("OTP_VALIDITY") + " " + minutes + ":" + seconds + " "
					+ RegistrationUIConstants.getMessageLanguageSpecific("MINUTES"));
		} catch (RuntimeException runtimeException) {
			LOGGER.error(runtimeException.getMessage(), runtimeException);
		}
	}

	/**
	 * To get the Sequence of which Login screen to be displayed
	 * 
	 * @param primaryStage primary Stage
	 */
	public void loadInitialScreen(Stage primaryStage) {
		try {
			showUserNameScreen(primaryStage);

			hasUpdate = RegistrationConstants.ENABLE.equalsIgnoreCase(
					getValueFromApplicationContext(RegistrationConstants.IS_SOFTWARE_UPDATE_AVAILABLE));
			if (hasUpdate) {
				// Update Application
				headerController.softwareUpdate(loginRoot, progressIndicator,
						ApplicationContext.getBundle(ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES)
								.getString("UPDATE_LATER"),
						baseService.isInitialSync());
			}
			
			changeNodeOrientation(contentPane);

		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load screen", exception);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN));
		}
	}

	private void showUserNameScreen(Stage primaryStage) throws IOException {
		fXComponents.setStage(primaryStage);
		loginRoot = BaseController.load(getClass().getResource(RegistrationConstants.INITIAL_PAGE));
		scene = getScene(loginRoot);
		pageFlow.loadPageFlow();

		if (userName != null) {
			userId.setText(userName);
			userName = null;
		}
		forgotUsrnme.setVisible(ApplicationContext.map().containsKey(RegistrationConstants.FORGOT_USERNAME_URL));

		Screen screen = Screen.getPrimary();
		Rectangle2D bounds = screen.getVisualBounds();
		primaryStage.setX(bounds.getMinX());
		primaryStage.setY(bounds.getMinY());
		primaryStage.setWidth(bounds.getWidth());
		primaryStage.setHeight(bounds.getHeight());
		primaryStage.setResizable(false);
		primaryStage.setScene(scene);
		primaryStage.getIcons().add(new Image(getClass().getResource(RegistrationConstants.LOGO).toExternalForm()));
		primaryStage.show();
	}

	/**
	 * Validate user id.
	 *
	 * @param event the event
	 */
	@SuppressWarnings("unchecked")
	public void validateUserId(ActionEvent event) {
		auditFactory.audit(AuditEvent.LOGIN_AUTHENTICATE_USER_ID, Components.LOGIN,
				userId.getText().isEmpty() ? "NA" : userId.getText(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		LOGGER.info("Validating Credentials entered through UI");

		if (userId.getText().isEmpty()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.USERNAME_FIELD_EMPTY));
			return;
		}
		if (baseService.isInitialSync()) { // For Initial SetUp
			initialSetUpOrNewUserLaunch();
			return;
		}

		try {
			ResponseDTO responseDTO = loginService.validateUser(userId.getText());
			List<ErrorResponseDTO> errorResponseDTOList = responseDTO.getErrorResponseDTOs();
			if (errorResponseDTOList != null && !errorResponseDTOList.isEmpty()) {
				generateAlertLanguageSpecific(RegistrationConstants.ERROR,errorResponseDTOList.get(0).getMessage());
				return;
			}

			UserDTO userDTO = (UserDTO) responseDTO.getSuccessResponseDTO().getOtherAttributes().get(RegistrationConstants.USER_DTO);
			userId.setText(userDTO.getId());

			if (validateInvalidLogin(userDTO, "")) {
				isUserNewToMachine = machineMappingService.isUserNewToMachine(userDTO.getId()).getErrorResponseDTOs() != null;
				if (isUserNewToMachine) {
					initialSetUpOrNewUserLaunch();
				} else {
					Set<String> roles = (Set<String>) responseDTO.getSuccessResponseDTO().getOtherAttributes().get(RegistrationConstants.ROLES_LIST);
					/** if the role is default,the login should always thru password and user onboard
					* has to be skipped*/
					if (Role.isDefaultUser(roles)) {
						initialSetUpOrNewUserLaunch();
						return;
					}

					loginList = loginService.getModesOfLogin(ProcessNames.LOGIN.getType(), roles);
					String loginMode = !loginList.isEmpty() ? loginList.get(RegistrationConstants.PARAM_ZERO) : null;
					LOGGER.debug("Retrieved corresponding Login mode");
					if (loginMode == null) {
						userIdPane.setVisible(false);
						errorPane.setVisible(true);
					} else {
						userIdPane.setVisible(false);
						loadLoginScreen(loginMode);
					}
				}
			}
		} catch (RegBaseUncheckedException regBaseUncheckedException) {
			LOGGER.error(regBaseUncheckedException.getMessage(), regBaseUncheckedException);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN));
		}
	}

	private void initialSetUpOrNewUserLaunch() {
		userIdPane.setVisible(false);
		loadLoginScreen(LoginMode.PASSWORD.toString());
	}

	/**
	 * 
	 * Validating User credentials on Submit
	 * 
	 * @param event event for validating credentials
	 */
	public void validateCredentials(ActionEvent event) {

		auditFactory.audit(AuditEvent.LOGIN_WITH_PASSWORD, Components.LOGIN, userId.getText(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
				"Validating Credentials entered through UI");

		LoginUserDTO loginUserDTO = new LoginUserDTO();
		loginUserDTO.setUserId(userId.getText());
		loginUserDTO.setPassword(password.getText());
		ApplicationContext.map().put(RegistrationConstants.USER_DTO, loginUserDTO);
		UserDTO userDTO = loginService.getUserDetail(userId.getText());

		boolean isInitialSetUp = baseService.isInitialSync();

		if(!isInitialSetUp) {
			validateUserCredentialsInLocal(userDTO);
			return;
		}

		if(!serviceDelegateUtil.isNetworkAvailable()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_INTERNET_CONNECTION));
			return;
		}

		try {
			if (SessionContext.create(userDTO, RegistrationConstants.PWORD, isInitialSetUp, isUserNewToMachine,	null)) {
				executePreLaunchTask(credentialsPane, passwordProgressIndicator, userDTO);
				return;
			}

		} catch (Exception exception) {
			LOGGER.error("Exception while getting AuthZ Token", exception);
		}

		//Any error on login
		generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_TO_GET_AUTH_TOKEN));
		SessionContext.destroySession();
		loadInitialScreen(ClientApplication.getPrimaryStage());
	}

	private void validateUserCredentialsInLocal(UserDTO userDTO) {
		if (password.getText().isEmpty()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PWORD_FIELD_EMPTY));
			return;
		}

		if(userDTO == null) {
			loadInitialScreen(ClientApplication.getPrimaryStage());
			return;
		}

		AuthenticationValidatorDTO authenticationValidatorDTO = new AuthenticationValidatorDTO();
		authenticationValidatorDTO.setUserId(userDTO.getId());
		authenticationValidatorDTO.setPassword(password.getText());

		try {
			if (SessionContext.create(userDTO, RegistrationConstants.PWORD, baseService.isInitialSync(), isUserNewToMachine,
					authenticationValidatorDTO)) {
				//In this case, only update of last login method and last login time is applicable
				validateInvalidLogin(userDTO, "");

				if(authTokenUtilService.hasAnyValidToken() && !ClientApplication.isSyncCompleted()) {
					LOGGER.info("As the sync was not handled in pre-loader, performing the sync now with valid token");
					executePreLaunchTask(credentialsPane, passwordProgressIndicator, userDTO);
				} else {
					credentialsPane.setVisible(false);
					loadNextScreen(userDTO, RegistrationConstants.PWORD);
				}
			} else {
				//In this case, as the session creation has failed, validation of login attempts and update of login failures count is handled
				validateInvalidLogin(userDTO, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.AUTHENTICATION_FAILURE));
			}
		} catch (RegBaseCheckedException | IOException exception) {
			LOGGER.error("", exception);
			generateAlert(RegistrationConstants.ALERT_INFORMATION,
							RegistrationUIConstants.getMessageLanguageSpecific(exception.getMessage().substring(0, 3)
							+ RegistrationConstants.UNDER_SCORE + RegistrationConstants.MESSAGE.toUpperCase()));
		}
	}

	@FXML
	private void moveToTop(){

		Label label = (Label) headingPane.getChildren().get(0);

		AnchorPane.setTopAnchor(label, 0.0);
		AnchorPane.setBottomAnchor(label,null);

	}

	/**
	 * Generate OTP based on EO username
	 * 
	 * @param event event for generating OTP
	 */
	@FXML
	public void generateOtp(ActionEvent event) {

		if (userId.getText().isEmpty()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.USERNAME_FIELD_EMPTY));
		} else {

			auditFactory.audit(AuditEvent.LOGIN_GET_OTP, Components.LOGIN, userId.getText(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			// Response obtained from server
			ResponseDTO responseDTO = otpGenerator.getOTP(userId.getText());

			if (responseDTO.getSuccessResponseDTO() != null) {
				// Enable submit button
				changeToOTPSubmitMode();

				// Generate alert to show OTP
				generateAlert(RegistrationConstants.ALERT_INFORMATION,
						RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.OTP_GENERATION_SUCCESS_MESSAGE));

			} else if (responseDTO.getErrorResponseDTOs() != null) {
				// Generate Alert to show INVALID USERNAME
				generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.OTP_GENERATION_ERROR_MESSAGE));

			}
		}
	}

	/**
	 * Validate User through username and otp
	 * 
	 * @param event event for validating OTP
	 */
	@FXML
	public void validateOTP(ActionEvent event) {

		auditFactory.audit(AuditEvent.LOGIN_SUBMIT_OTP, Components.LOGIN, userId.getText(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		if (validations.validateTextField(otpPane, otp, otp.getId(), true, ApplicationContext.applicationLanguage())) {

			UserDTO userDTO = loginService.getUserDetail(userId.getText());

			boolean otpLoginStatus = false;

			AuthenticationValidatorDTO authenticationValidatorDTO = new AuthenticationValidatorDTO();
			authenticationValidatorDTO.setUserId(userId.getText());
			authenticationValidatorDTO.setOtp(otp.getText());

			try {
				if (SessionContext.create(userDTO, RegistrationConstants.OTP, false, false,
						authenticationValidatorDTO)) {
					otpLoginStatus = validateInvalidLogin(userDTO, "");
				} else {
					otpLoginStatus = validateInvalidLogin(userDTO,
							RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.OTP_VALIDATION_ERROR_MESSAGE));
				}
			} catch (RegBaseCheckedException | IOException exception) {
				generateAlert(RegistrationConstants.ALERT_INFORMATION,
						RegistrationUIConstants.getMessageLanguageSpecific(exception.getMessage().substring(0, 3)
								+ RegistrationConstants.UNDER_SCORE + RegistrationConstants.MESSAGE.toUpperCase()));
			}
			if (otpLoginStatus) {
				otpPane.setVisible(false);
				int otpExpirySeconds = Integer
						.parseInt((getValueFromApplicationContext(RegistrationConstants.OTP_EXPIRY_TIME)).trim());
				int minutes = otpExpirySeconds / 60;
				String seconds = String.valueOf(otpExpirySeconds % 60);
				seconds = seconds.length() < 2 ? "0" + seconds : seconds;
				otpValidity.setText(RegistrationUIConstants.getMessageLanguageSpecific("OTP_VALIDITY") + " " + minutes + ":" + seconds + " "
						+ RegistrationUIConstants.getMessageLanguageSpecific("MINUTES"));
				loadNextScreen(userDTO, RegistrationConstants.OTP);

			}

		}
	}

	public void streamFace() {

		try {
			streamer.startStream(bioService.getStream(RegistrationConstants.FACE_FULLFACE), faceImage, null);
		} catch (RegBaseCheckedException regBaseCheckedException) {
			LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(regBaseCheckedException));
		}
	}

	/**
	 * Validate User through username and fingerprint
	 * 
	 * @param event event for capturing fingerprint
	 */
	public void captureFingerPrint(ActionEvent event) {

		auditFactory.audit(AuditEvent.LOGIN_WITH_FINGERPRINT, Components.LOGIN, userId.getText(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
				"Capturing finger print for validation");

		UserDTO userDTO = loginService.getUserDetail(userId.getText());

		AuthenticationValidatorDTO authenticationValidatorDTO = new AuthenticationValidatorDTO();
		authenticationValidatorDTO.setUserId(userId.getText());

		executeValidationTask(userDTO, RegistrationConstants.FINGERPRINT_UPPERCASE, false, false,
				authenticationValidatorDTO, fingerprintPane, fpProgressIndicator,
				RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.FINGER_PRINT_MATCH), RegistrationConstants.FINGERPRINT);
	}

	/**
	 * Validate User through username and Iris
	 * 
	 * @param event event for capturing Iris
	 */
	public void captureIris(ActionEvent event) {

		auditFactory.audit(AuditEvent.LOGIN_WITH_IRIS, Components.LOGIN, userId.getText(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
				"Capturing the iris for validation");

		UserDTO userDTO = loginService.getUserDetail(userId.getText());

		AuthenticationValidatorDTO authenticationValidatorDTO = new AuthenticationValidatorDTO();
		authenticationValidatorDTO.setUserId(userId.getText());
		authenticationValidatorDTO.setAuthValidationType("single");

		executeValidationTask(userDTO, RegistrationConstants.IRIS, false, false, authenticationValidatorDTO, irisPane,
				irisProgressIndicator, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.IRIS_MATCH), RegistrationConstants.IRIS);
	}

	/**
	 * Validate User through username and face
	 * 
	 * @param event event to capture face
	 */
	public void captureFace(ActionEvent event) {

		auditFactory.audit(AuditEvent.LOGIN_WITH_FACE, Components.LOGIN, userId.getText(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID, "Capturing face for validation");

		UserDTO userDTO = loginService.getUserDetail(userId.getText());

		AuthenticationValidatorDTO authenticationValidatorDTO = new AuthenticationValidatorDTO();
		authenticationValidatorDTO.setUserId(userId.getText());

		executeValidationTask(userDTO, RegistrationConstants.FACE, false, false, authenticationValidatorDTO, facePane,
				faceProgressIndicator, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.FACE_MATCH), RegistrationConstants.FACE);
	}

	private void executeValidationTask(UserDTO userDTO, String loginMethod, boolean isInitialSetUp,
			boolean isUserNewToMachine, AuthenticationValidatorDTO authenticationValidatorDTO, AnchorPane pane,
			ProgressIndicator progressIndicator, String errorMessage, String loginMode) {
		pane.setDisable(true);
		progressIndicator.setVisible(true);

		Service<Boolean> taskService = new Service<Boolean>() {
			@Override
			protected Task<Boolean> createTask() {
				return new Task<Boolean>() {
					/*
					 * (non-Javadoc)
					 * 
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected Boolean call() {
						try {
							return SessionContext.create(userDTO, loginMethod, isInitialSetUp, isUserNewToMachine,
									authenticationValidatorDTO);
						} catch (RegBaseCheckedException | IOException exception) {
							LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
									"Exception while getting the scanned biometrics for user login: caused by "
											+ ExceptionUtils.getStackTrace(exception));
							generateAlert(RegistrationConstants.ERROR,
									RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.BIOMETRIC_SCANNING_ERROR));
						}
						return false;
					}
				};
			}
		};

		progressIndicator.progressProperty().bind(taskService.progressProperty());
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				boolean bioLoginStatus = false;
				if (taskService.getValue()) {
					bioLoginStatus = validateInvalidLogin(userDTO, "");
				} else {
					bioLoginStatus = validateInvalidLogin(userDTO, errorMessage);
				}
				pane.setDisable(false);
				progressIndicator.setVisible(false);

				if (bioLoginStatus) {
					LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							loginMode + " validation success");
					pane.setVisible(false);
					loadNextScreen(userDTO, loginMode);
				} else {
					LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							loginMode + " validation failed");
				}

				LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
						loginMode + " validation done");
			}
		});
	}

	/**
	 * Mode of login with set of fields enabling and disabling
	 */
	private void changeToOTPSubmitMode() {
		submit.setDisable(false);
		otpSubmit.setDisable(false);
		getOTP.setVisible(false);
		resend.setVisible(true);
		otpValidity.setVisible(true);
	}

	/**
	 * Load login screen depending on Loginmode
	 * 
	 * @param loginMode login screen to be loaded
	 */
	public void loadLoginScreen(String loginMode) {

		switch (loginMode.toUpperCase()) {
		case RegistrationConstants.OTP:
			otpPane.setVisible(true);
			break;
		case RegistrationConstants.PWORD:
			credentialsPane.setVisible(true);
			setPwordLabelVisibility();
			break;
		case RegistrationConstants.FINGERPRINT_UPPERCASE:
			fingerprintPane.setVisible(true);
			break;
		case RegistrationConstants.IRIS:
			irisPane.setVisible(true);
			break;
		case RegistrationConstants.FACE:
			facePane.setVisible(true);
			break;
		default:
			credentialsPane.setVisible(true);
			setPwordLabelVisibility();
		}

		if (!loginList.isEmpty()) {
			loginList.remove(RegistrationConstants.PARAM_ZERO);
		}
	}

	private void setPwordLabelVisibility() {
		forgotPword.setVisible(ApplicationContext.map().containsKey(RegistrationConstants.FORGOT_PWORD_URL));
	}

	/**
	 * Redirects to mosip.io in case of user forgot user name
	 * 
	 * @param event event for forgot user name
	 */
	public void forgotUsrname(ActionEvent event) {
		forgotUsrnme.setOnAction(e -> {
			if (Desktop.isDesktopSupported()) {
				try {
					Desktop.getDesktop().browse(new URI(ApplicationContext
							.getStringValueFromApplicationMap(RegistrationConstants.FORGOT_USERNAME_URL)));
				} catch (IOException ioException) {
					LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
				} catch (URISyntaxException uriSyntaxException) {
					LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							uriSyntaxException.getMessage() + ExceptionUtils.getStackTrace(uriSyntaxException));
				}
			}
		});
	}

	/**
	 * Redirects to mosip.io in case of user forgot pword
	 * 
	 * @param event event for forgot pword
	 */
	public void forgotPword(ActionEvent event) {
		forgotPword.setOnAction(e -> {
			if (Desktop.isDesktopSupported()) {
				try {
					String url = ApplicationContext
							.getStringValueFromApplicationMap(RegistrationConstants.FORGOT_PWORD_URL);
					if (url.toUpperCase().contains(RegistrationConstants.EMAIL_PLACEHOLDER)) {
						UserDTO userDTO = loginService.getUserDetail(userId.getText());
						url = url.replace(RegistrationConstants.EMAIL_PLACEHOLDER, userDTO.getEmail());
					}
					Desktop.getDesktop().browse(new URI(url));
				} catch (IOException ioException) {
					LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
				} catch (URISyntaxException uriSyntaxException) {
					LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							uriSyntaxException.getMessage() + ExceptionUtils.getStackTrace(uriSyntaxException));
				}
			}
		});
	}

	/**
	 * Loading next login screen in case of multifactor authentication
	 * 
	 * @param userDTO   the userDetail
	 * @param loginMode the loginMode
	 */
	private void loadNextScreen(UserDTO userDTO, String loginMode) {

		if (!loginList.isEmpty()) {

			LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
					"Loading next login screen in case of multifactor authentication");

			loadLoginScreen(loginList.get(RegistrationConstants.PARAM_ZERO));

		} else {

			if (null != SessionContext.userContext().getUserId()) {

				auditFactory.audit(AuditEvent.NAV_HOME, Components.LOGIN, userId.getText(),
						AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

				try {

					LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID, "Loading Home screen");
					schedulerUtil.startSchedulerUtil();
					loginList.clear();

					BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
					// to add events to the stage
					getStage();

					userDTO.setLastLoginMethod(loginMode);
					userDTO.setLastLoginDtimes(Timestamp.valueOf(DateUtils.getUTCCurrentDateTime()));
					userDTO.setUnsuccessfulLoginCount(RegistrationConstants.PARAM_ZERO);

					loginService.updateLoginParams(userDTO);
				} catch (IOException | RegBaseCheckedException | RuntimeException exception) {
					LOGGER.error(exception.getMessage(), exception);
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN));
				}
			}
		}
	}

	public void executePreLaunchTask(Pane pane, ProgressIndicator progressIndicator, UserDTO userDTO) {
		boolean isInitialSetUp = baseService.isInitialSync();
		progressIndicator.setVisible(true);
		pane.setDisable(true);

		/**
		 * This anonymous service class will do the pre application launch task
		 * progress.
		 * 
		 */
		taskService = new Service<List<String>>() {
			@Override
			protected Task<List<String>> createTask() {
				return /**
						 * @author SaravanaKumar
						 *
						 */
				new Task<List<String>>() {
					/*
					 * (non-Javadoc)
					 * 
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected List<String> call() {

						LOGGER.info("REGISTRATION - INITIAL_SYNC - LOGIN_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
								"Handling all the sync activities before login");
						if (serviceDelegateUtil.isNetworkAvailable()
								&& (baseService.isInitialSync() || authTokenUtilService.hasAnyValidToken()))
							return loginService.initialSync(RegistrationConstants.JOB_TRIGGER_POINT_SYSTEM);
						else {
							List<String> list = new ArrayList<>();
							list.add(RegistrationConstants.SUCCESS);
							return list;
						}
					}
				};
			}
		};

		progressIndicator.progressProperty().bind(taskService.progressProperty());
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				if (taskService.getValue().contains(RegistrationConstants.FAILURE)) {
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SYNC_CONFIG_DATA_FAILURE));
					if (isInitialSetUp) {
						loadInitialScreen(ClientApplication.getPrimaryStage());
						return;
					}
				} else if (taskService.getValue().contains(RegistrationConstants.AUTH_TOKEN_NOT_RECEIVED_ERROR)) {
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.ALERT_AUTH_TOKEN_NOT_FOUND));
					if (isInitialSetUp) {
						loadInitialScreen(ClientApplication.getPrimaryStage());
						return;
					}
				} else if (taskService.getValue().contains(RegistrationConstants.RESTART)) {
					restartApplication();
				} else if (taskService.getValue().contains(RegistrationConstants.SUCCESS)) {
					if (isInitialSetUp) {
						// update initial set up flag
						globalParamService.update(RegistrationConstants.INITIAL_SETUP, RegistrationConstants.DISABLE);
						restartApplication();
					} else {
						//As the sync is successful, navigating the user to the next screen						
						pane.setVisible(false);
						loadNextScreen(userDTO, RegistrationConstants.PWORD);
					}					
				}
				pane.setDisable(false);
				progressIndicator.setVisible(false);
			}

		});

	}

	/**
	 * Validating invalid number of login attempts
	 * 
	 * @param userDTO      user details
	 * @param errorMessage
	 * @return boolean
	 */
	private boolean validateInvalidLogin(UserDTO userDTO, String errorMessage) {
		LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID, "Fetching invalid login params");

		int invalidLoginCount = Integer
				.parseInt(getValueFromApplicationContext(RegistrationConstants.INVALID_LOGIN_COUNT));

		int invalidLoginTime = Integer
				.parseInt(getValueFromApplicationContext(RegistrationConstants.INVALID_LOGIN_TIME));

		String unlockMessage = String.format("%s %s %s %s %s", RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.USER_ACCOUNT_LOCK_MESSAGE_NUMBER),
				String.valueOf(invalidLoginCount), RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.USER_ACCOUNT_LOCK_MESSAGE),
				String.valueOf(invalidLoginTime), RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.USER_ACCOUNT_LOCK_MESSAGE_MINUTES));

		LOGGER.info("Invoking validation of login attempts");

		String val = loginService.validateInvalidLogin(userDTO, errorMessage, invalidLoginCount, invalidLoginTime);

		switch (val.toLowerCase()){
			case "error":
				generateAlert(RegistrationConstants.ERROR, unlockMessage);
				loadLoginScreen();
				break;
			case "true":
				return true;
			default:
				generateAlert(RegistrationConstants.ERROR, errorMessage);
		}

		LOGGER.info("Validated number of login attempts, failed!");
		return false;
	}

	/**
	 * Redirects to mosip username page
	 * 
	 * @param event event for go back to username page
	 */
	public void back(ActionEvent event) {

		LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
				"Started navigating back to user name page");

		String usrId = userId.getText();
		try {
			showUserNameScreen(ClientApplication.getPrimaryStage());
			if (usrId != null) {

				LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
						"Setting previous username after navigate");
				userId.setText(usrId);
			}
		} catch (IOException ioException) {

			LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN));
		}

	}
}