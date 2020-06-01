package com.gymfitness.backend.controllers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gymfitness.backend.models.UserLevel;
import com.gymfitness.backend.models.Billing;
import com.gymfitness.backend.models.EnumLevel;
import com.gymfitness.backend.models.User;
import com.gymfitness.backend.payload.request.LoginRequest;
import com.gymfitness.backend.payload.request.SignUpRequest;
import com.gymfitness.backend.payload.request.UserRequest;
import com.gymfitness.backend.payload.response.JwtResponse;
import com.gymfitness.backend.payload.response.MessageResponse;
import com.gymfitness.backend.repositories.BillingRepository;
import com.gymfitness.backend.repositories.UserLevelRepository;
import com.gymfitness.backend.repositories.UserRepository;
import com.gymfitness.backend.security.jwt.JwtUtils;
import com.gymfitness.backend.security.services.UserDetailsImpl;

@RestController
@CrossOrigin
@RequestMapping("/api/auth")
public class AuthController {
	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BillingRepository billingRepository;

	@Autowired
	UserLevelRepository userLevelRepository;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JwtUtils jwtUtils;

	@PostMapping("/cancel")
	public ResponseEntity<?> cancel(@Valid @RequestBody UserRequest request) {
		User user = userRepository.findByEmail(request.getEmail())
						.orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + request.getEmail()));

		user.setCancelled(true);
		userRepository.save(user);

		return ResponseEntity.ok(new MessageResponse("Membership cancelled successfully!"));
	}

	@PostMapping("/changepass")
	public ResponseEntity<?> changeUserPassword(@Valid @RequestBody LoginRequest request) {
		User user = userRepository.findByEmail(request.getEmail())
						.orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + request.getEmail()));

		user.setPassword(encoder.encode(request.getPassword()));
		userRepository.save(user);

		return ResponseEntity.ok(new MessageResponse("Password changed successfully!"));
	}

	@PostMapping("/signin")
	public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
		User user = userRepository.findByEmail(loginRequest.getEmail())
			.orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + loginRequest.getEmail()));

		if (user.getCancelled() == true) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: Account has been Cancelled"));
		}

		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

		SecurityContextHolder.getContext().setAuthentication(authentication);
		String jwt = jwtUtils.generateJwtToken(authentication);
		
		UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
		
		List<String> levels = userDetails.getAuthorities().stream()
				.map(item -> item.getAuthority())
				.collect(Collectors.toList());

		

		return ResponseEntity.ok(new JwtResponse(jwt, 
												 userDetails.getId(), 
												 userDetails.getFirstname(),
												 userDetails.getSurname(),
												 userDetails.getEmail(), 
												 levels));
	}

	@PostMapping("/signup")
	public ResponseEntity<?> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) {
		if (userRepository.existsByEmail(signUpRequest.getEmail())) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: Email is already in use!"));
		}

		// Create new user's account
		User user = new User(signUpRequest.getFirstname(),
							 signUpRequest.getSurname(), 
							 signUpRequest.getEmail(),
							 encoder.encode(signUpRequest.getPassword()));

		Billing billing = new Billing(signUpRequest.getBankno(), signUpRequest.getClearingno(), user);

		String strLevels = signUpRequest.getLevel();
		Set<UserLevel> levels = new HashSet<>();

		if (strLevels == null) {
			UserLevel memberLevel = userLevelRepository.findByLevelName(EnumLevel.MEMBER_BASIC)
					.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
			levels.add(memberLevel);
		} else {
			switch (strLevels) {
			case "MEMBER_STANDARD":
				UserLevel standardLevel = userLevelRepository.findByLevelName(EnumLevel.MEMBER_STANDARD)
						.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
				levels.add(standardLevel);

				break;
			case "MEMBER_PREMIUM":
				UserLevel premiumLevel = userLevelRepository.findByLevelName(EnumLevel.MEMBER_PREMIUM)
						.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
				levels.add(premiumLevel);

				break;
			case "ADMIN":
				UserLevel adminLevel = userLevelRepository.findByLevelName(EnumLevel.ADMIN)
						.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
				levels.add(adminLevel);

				break;
			case "INSTRUCTOR":
				UserLevel instructorLevel = userLevelRepository.findByLevelName(EnumLevel.INSTRUCTOR)
						.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
				levels.add(instructorLevel);

				break;
			default:
				UserLevel memberLevel = userLevelRepository.findByLevelName(EnumLevel.MEMBER_BASIC)
						.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
				levels.add(memberLevel);
			}
		}

		user.setUserLevel(levels);
		userRepository.save(user);
		billingRepository.save(billing);

		return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
	}
}