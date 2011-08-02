package com.rackspacecloud.android;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rackspace.cloud.loadbalancer.api.client.Node;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class AddExternalNodeActivity extends CloudActivity {

	private final String[] CONDITIONS = {"Enabled", "Disabled", "Draining"};
	private String selectedPort;
	private String selectedIp;
	private String selectedWeight;
	private boolean weighted;
	private String selectedCondition;
	private Spinner conditionSpinner;
	private EditText ipAddress;
	private EditText weightText;
	private Node node;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.addexternalnode);
		weighted = (Boolean) this.getIntent().getExtras().get("weighted");
		node = (Node) this.getIntent().getExtras().get("node");
		restoreState(savedInstanceState);
	} 

	protected void restoreState(Bundle state) {
		super.restoreState(state);
		setupInputs();
	}

	private void setupInputs(){
		
		ipAddress = (EditText) findViewById(R.id.ip_address);
		
		weightText = (EditText) findViewById(R.id.node_weight_text);

		//if algorithm is not weighted then then node's weight will be null
		if(!weighted){
			TextView weightLabel = (TextView) findViewById(R.id.node_weight_label);
			weightLabel.setVisibility(View.GONE);
			weightText.setVisibility(View.GONE);
		}

		loadConditionSpinner();
		setUpButton();

		if(node != null){
			ipAddress.setText(node.getAddress());
			weightText.setText(node.getWeight());
			((EditText)findViewById(R.id.node_port_text)).setText(node.getPort());
			conditionSpinner.setSelection(getLocation(CONDITIONS, node.getCondition()));
		}
	}
	
	private int getLocation(Object[] objects, Object object){
		for(int i = 0; i < objects.length; i++){
			if(object.equals(objects[i])){
				return i;
			}
		}
		return 0;
	}

	private void setUpButton(){
		Button submit = (Button) findViewById(R.id.add_node_button);
		submit.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				selectedIp = ipAddress.getText().toString();
				selectedPort = ((EditText)findViewById(R.id.node_port_text)).getText().toString();
				selectedWeight = weightText.getText().toString();
				if(!validPort(selectedPort)){
					showAlert("Error", "Must have a protocol port number that is between 1 and 65535.");
				} else if(!(weightText.getVisibility() == View.GONE || (weightText.getVisibility() != View.GONE && validWeight(selectedWeight)))){
					showAlert("Error", "Weight must be between 1 and 100.");
				} else if(ipAddress.getText().toString().equals("")){
					//TODO use regex to validate the ip for IPV4 and IPV6
					showAlert("Error", "Enter an IP Address");
				} else if(!validIp(ipAddress.getText().toString())) {
					showAlert("Error", "Enter a valid IP Address");
				} else {
					Intent data = new Intent();
					data.putExtra("nodeIp", selectedIp);
					data.putExtra("nodePort", selectedPort);
					data.putExtra("nodeCondition", selectedCondition);
					data.putExtra("nodeWeight", selectedWeight);
					setResult(RESULT_OK, data);
					finish();
				}

			}
		});
	}

	private void loadConditionSpinner(){
		conditionSpinner = (Spinner) findViewById(R.id.node_condition_spinner);

		ArrayAdapter<String> conditionAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, CONDITIONS);
		conditionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		conditionSpinner.setAdapter(conditionAdapter);

		conditionSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				selectedCondition = CONDITIONS[pos];	
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {

			}

		});
	}
	
	//basic ip validation just checks that the string
	//is only composed of letters, numbers, ., :
	private static boolean validIp(String ip){
		//Enter regex
		Pattern pattern = Pattern.compile("[a-zA-Z0-9.:]+");
		Matcher match = pattern.matcher(ip);
		return match.matches();
	}

	private boolean validPort(String port){
		boolean result;
		try{
			result = !port.equals("") && Integer.valueOf(port) > 0 && Integer.valueOf(port) < 65536;
		} catch (NumberFormatException e) {
	    	result = false;
	    }
	    return result;
	}

	private Boolean validWeight(String weight){
		if(weight.equals("")){
			return false;
		}
		else{
			int w;
			try{
				w = Integer.valueOf(weight);
			} catch (NumberFormatException e){
				return false;
			}
			return w >= 1 && w <= 100 ;
		}
	}

	public void onBackPressed(){
		setResult(RESULT_CANCELED);
		finish();
	}

}
