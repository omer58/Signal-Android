package org.securityed.securesms;

import java.util.Date;
import java.util.GregorianCalendar;

public class ApplicationTimeTracker {


    private Date openDate;
    private Date closeDate;

    private long timeInSettings;

    private long timeInConversationList;

    private long timeInConversation;

    public ApplicationTimeTracker(){

        openDate = GregorianCalendar.getInstance().getTime();
        timeInSettings = 0;
        timeInConversation = 0;
        timeInConversationList = 0;

    }


    public void setCloseDate( Date date){
        closeDate = date;
    }


    public void addToConversationListTimeElapsed(long timeElapsed){
        timeInConversationList += timeElapsed;
    }

    public void addToConversationTimeElapsed(long timeElapsed){
        timeInConversation += timeElapsed;
    }

    public long getTotalTimeInConversationList(){
        return timeInConversationList;
    }

    public long getTotalTimeInConversation(){
        return timeInConversation;
    }

    public long getTotalTimeOpen(){
        return closeDate.getTime() - openDate.getTime();
    }

    public Date getOpenDate(){
        return openDate;
    }

    public String getScreenTimeLog(){

        return "_" + getTotalTimeOpen() + "_" + getTotalTimeInConversationList() + "_" + getTotalTimeInConversation() ;
    }
}
