set -x
set -e

git config --global log.mailmap true

UNKNOWN_USERS=`\
git log -1000 --oneline --format='%aN <%aE>' | sort -u |\
    grep -iv "^Aaditi Joag <aaditi.joag@harness.io>$" |\
    grep -iv "^Aaditya Kumar <aaditya.kumar@harness.io>$" |\
    grep -iv "^Abhijith V Mohan <abhijith.mohan@harness.io>$" |\
    grep -iv "^Abhinav Hinger <abhinav.hinger@harness.io>$" |\
    grep -iv "^Abhinav Singh <abhinav.singh@harness.io>$" |\
    grep -iv "^Adam Hancock <adam.hancock@harness.io>$" |\
    grep -iv "^Adwait Bhandare <adwait.bhandare@harness.io>$" |\
    grep -iv "^Akash Nagarajan <akash.nagarajan@harness.io>$" |\
    grep -iv "^akriti-harness <akriti.garg@harness.io>$" |\
    grep -iv "^Aleksandar Radisavljevic <aleksandar.radisavljevic@harness.io>$" |\
    grep -iv "^Alexandr Gorodetki <alexandr.gorodetki@harness.io>$" |\
    grep -iv "^Alexandru Bosii <alexandru.bosii@harness.io>$" |\
    grep -iv "^Alexandru Casian <alexandru.casian@harness.io>$" |\
    grep -iv "^Alexei Stirbul <alexei.stirbul@harness.io>$" |\
    grep -iv "^Aman Singh <aman.singh@harness.io>$" |\
    grep -iv "^aman-harness <aman.singh@harness.io>$" |\
    grep -iv "^aman-iitj <aman.singh@harness.io>$" |\
    grep -iv "^Anil Chowdhury <anil.chowdhury@harness.io>$" |\
    grep -iv "^Ankit Singhal <ankit.singhal@harness.io>$" |\
    grep -iv "^Ankush Shaw <ankush.shaw@harness.io>$" |\
    grep -iv "^Anshul Anshul <anshul@harness.io>$" |\
    grep -iv "^Anubhaw Srivastava <anubhaw@harness.io>$" |\
    grep -iv "^Archit Singla <archit.singla@harness.io>$" |\
    grep -iv "^Arvind Choudhary <arvind.choudhary@harness.io>$" |\
    grep -iv "^Bojana Milovanovic <bojana.milovanovic@harness.io>$" |\
    grep -iv "^Boopesh Shanmugam <boopesh.shanmugam@harness.io>$" |\
    grep -iv "^bot-harness <bot@harness.io>$" |\
    grep -iv "^Brett Zane <brett@harness.io>$" |\
    grep -iv "^Brijesh Dhakar <brijesh.dhakar@harness.io>$" |\
    grep -iv "^Christopher Clark <christopher.clark@harness.io>$" |\
    grep -iv "^CI Bot <bot@harness.io>$" |\
    grep -iv "^Deepak Patankar <deepak.patankar@harness.io>$" |\
    grep -iv "^Deepak Puthraya <deepak.puthraya@harness.io>$" |\
    grep -iv "^Deepak Puthraya \[Harness\] <deepak.puthraya@harness.io>$" |\
    grep -iv "^Dhruv Upadhyay <dhruv.upadhyay@harness.io>$" |\
    grep -iv "^Dinesh Garg <dinesh.garg@harness.io>$" |\
    grep -iv "^Diptiman Adak <diptiman.adak@harness.io>$" |\
    grep -iv "^diptiman.adak <diptiman.adak@harness.io>$" |\
    grep -iv "^Duc Nguyen <duc@harness.io>$" |\
    grep -iv "^Garvit Pahal <garvit.pahal@harness.io>$" |\
    grep -iv "^George Georgiev <george@harness.io>$" |\
    grep -iv "^Guna Chandrasekaran <guna.chandrasekaran@harness.io>$" |\
    grep -iv "^Hannah Tang <hannah.tang@harness.io>$" |\
    grep -iv "^Harsh Jain <harsh.jain@harness.io>$" |\
    grep -iv "^Hitesh Aringa <hitesh.aringa@harness.io>$" |\
    grep -iv "^Igor Gere <igor.gere@harness.io>$" |\
    grep -iv "^Inderpreet Chera <inderpreet.chera@harness.io>$" |\
    grep -iv "^Ishan Bhanuka <ishan.bhanuka@harness.io>$" |\
    grep -iv "^Ivan Mijailovic <ivan.mijailovic@harness.io>$" |\
    grep -iv "^Jatin Shridhar <jatin@harness.io>$" |\
    grep -iv "^Johnny Liu <johnny.liu@harness.io>$" |\
    grep -iv "^Juhi Agrawal <juhi.agrawal@harness.io>$" |\
    grep -iv "^K Rohit Reddy <rohit.reddy@harness.io>$" |\
    grep -iv "^Kamal Joshi <kamal.joshi@harness.io>$" |\
    grep -iv "^Kanhaiya Rathi <kanhaiya.rathi@harness.io>$" |\
    grep -iv "^Karan Siwach <karan.siwach@harness.io>$" |\
    grep -iv "^Lazar Matovic <lazar.matovic@harness.io>$" |\
    grep -iv "^Lucas Mari <lucas.mari@harness.io>$" |\
    grep -iv "^Magdalene Lee <magdalene.lee@harness.io>$" |\
    grep -iv "^Mark Lu <mark.lu@harness.io>$" |\
    grep -iv "^Marko Barjaktarovic <marko.barjaktarovic@harness.io>$" |\
    grep -iv "^Matt Hill <matt@harness.io>$" |\
    grep -iv "^Matt Lin <matthew.lin@harness.io>$" |\
    grep -iv "^Meenakshi Raikwar <meenakshi.raikwar@harness.io>$" |\
    grep -iv "^Mehul Kasliwal <mehul.kasliwal@harness.io>$" |\
    grep -iv "^Michael Katz <michael.katz@harness.io>$" |\
    grep -iv "^Milan Balaban <milan.balaban@harness.io>$" |\
    grep -iv "^Milos Paunovic <milos.paunovic@harness.io>$" |\
    grep -iv "^Mohit Garg <mohit.garg@harness.io>$" |\
    grep -iv "^Mohit Kurani <mohit.kurani@harness.io>$" |\
    grep -iv "^Naman Verma <naman.verma@harness.io>$" |\
    grep -iv "^Nandan Chandrashekar <nandan.chandrashekar@harness.io>$" |\
    grep -iv "^Nataraja Maruthi <nataraja@harness.io>$" |\
    grep -iv "^Nemanja Lukovic <nemanja.lukovic@harness.io>$" |\
    grep -iv "^Nicolas Bantar <nicolas.bantar@harness.io>$" |\
    grep -iv "^Nikhil Ranjan <nikhil.ranjan@harness.io>$" |\
    grep -iv "^Nikola Obucina <nikola.obucina@harness.io>$" |\
    grep -iv "^Nikunj Badjatya <nikunj.badjatya@harness.io>$" |\
    grep -iv "^Nitin Kotwal <nitin.kotwal@harness.io>$" |\
    grep -iv "^Parnian Zargham <parnian@harness.io>$" |\
    grep -iv "^Piyush Patel <piyush.patel@harness.io>$" |\
    grep -iv "^Pooja Singhal <pooja.singhal@harness.io>$" |\
    grep -iv "^Prabu Rajendran <prabu.rajendran@harness.io>$" |\
    grep -iv "^Pranjal Kumar <pranjal@harness.io>$" |\
    grep -iv "^Prashant Pal <prashant.pal@harness.io>$" |\
    grep -iv "^Prashant Sharma <prashant.sharma@harness.io>$" |\
    grep -iv "^prashantsharma04 <prashant.sharma@harness.io>$" |\
    grep -iv "^Prasun Banerjee <prasun.banerjee@harness.io>$" |\
    grep -iv "^Praveen Kambam Sugavanam <praveen.sugavanam@harness.io>$" |\
    grep -iv "^Praveen Sugavanam <praveen.sugavanam@harness.io>$" |\
    grep -iv "^Puneet Saraswat <puneet.saraswat@harness.io>$" |\
    grep -iv "^Raghvendra Singh <raghu@harness.io>$" |\
    grep -iv "^Raghvendra Singh <raghvendra.singh@harness.io>$" |\
    grep -iv "^Raj Patel <raj.patel@harness.io>$" |\
    grep -iv "^Rama Tummala <rama@harness.io>$" |\
    grep -iv "^Rathnakara Malatesha <rathna@harness.io>$" |\
    grep -iv "^Raunak Agrawal <raunak.agrawal@harness.io>$" |\
    grep -iv "^Reetika <mallavarapu.reetika@harness.io>$" |\
    grep -iv "^Rihaz Zahir <rihaz.zahir@harness.io>$" |\
    grep -iv "^Rishi Singh <rishi@harness.io>$" |\
    grep -iv "^Rohit Karelia <rohit.karelia@harness.io>$" |\
    grep -iv "^Rohit Kumar <rohit.kumar@harness.io>$" |\
    grep -iv "^Rushabh Shah <rushabh.shah@harness.io>$" |\
    grep -iv "^Sahil Hindwani <sahil.hindwani@harness.io>$" |\
    grep -iv "^Sahithi Kolichala <sahithi@harness.io>$" |\
    grep -iv "^Sainath Batthala <sainath.batthala@harness.io>$" |\
    grep -iv "^Sandesh Katta <katta.sandesh@harness.io>$" |\
    grep -iv "^Sanja Jokic <sanja.jokic@harness.io>$" |\
    grep -iv "^Sanyasi Naidu Annepu <sanyasi.naidu@harness.io>$" |\
    grep -iv "^Satyam Shanker <satyam.shanker@harness.io>$" |\
    grep -iv "^Shaswat Deep <shaswat.deep@harness.io>$" |\
    grep -iv "^Shivakumar Ningappa <shivakumar.ningappa@harness.io>$" |\
    grep -iv "^Shubham Agrawal <shubham.agrawal@harness.io>$" |\
    grep -iv "^Shubhanshu Verma <shubhanshu.verma@harness.io>$" |\
    grep -iv "^Sowmya K <sowmya.k@harness.io>$" |\
    grep -iv "^Sri Hari Chidella <srihari.chidella@harness.io>$" |\
    grep -iv "^Srinivasa Gurubelli <srinivas@harness.io>$" |\
    grep -iv "^Sriram Parthasarathy <sriram@harness.io>$" |\
    grep -iv "^Sujay Sharma <sujay.sharma@harness.io>$" |\
    grep -iv "^Sunil Shetty <sunil@harness.io>$" |\
    grep -iv "^Swagat Konchada <swagat@harness.io>$" |\
    grep -iv "^Swamy Sambamurthy <swamy@harness.io>$" |\
    grep -iv "^Swapnil Mahajan <swapnil@harness.io>$" |\
    grep -iv "^Tan Nhu <tan@harness.io>$" |\
    grep -iv "^Tathagat Chaurasiya <tathagat.chaurasiya@harness.io>$" |\
    grep -iv "^Tudor Macari <tudor.macari@harness.io>$" |\
    grep -iv "^Ujjawal Prasad <ujjawal.prasad@harness.io>$" |\
    grep -iv "^Utkarsh Gupta <utkarsh.gupta@harness.io>$" |\
    grep -iv "^Utsav Krishnan <utsav.krishnan@harness.io>$" |\
    grep -iv "^Vaibhav Singhal <vaibhav.si@harness.io>$" |\
    grep -iv "^Vaibhav Tulsyan <vaibhav.tulsyan@harness.io>$" |\
    grep -iv "^Vardan Bansal <vardan.bansal@harness.io>$" |\
    grep -iv "^Vasile Glijin <vasile.glijin@harness.io>$" |\
    grep -iv "^Vasuki Kalluri <vasuki.kalluri@harness.io>$" |\
    grep -iv "^Venkatesh Kotrike <venkatesh.kotrike@harness.io>$" |\
    grep -iv "^Vikas Naiyar <vikas.naiyar@harness.io>$" |\
    grep -iv "^Vikas Singh <vikas.singh@harness.io>$" |\
    grep -iv "^Vistaar Juneja <vistaar.juneja@harness.io>$" |\
    grep -iv "^Vojin Djukic <vojin.djukic@harness.io>$" |\
    grep -iv "^Vuk Skobalj <vuk.harness@harness.io>$" |\
    grep -iv "^Vuk Skobalj <vuk.skobalj@harness.io>$" |\
    grep -iv "^Yogesh Chauhan <yogesh.chauhan@harness.io>$"` || :

if [ ! -z "$UNKNOWN_USERS" ]
then
    echo "Unknown user name and/or email"
    echo "$UNKNOWN_USERS"
    exit 1
fi


EXCEPTIONS="^`cat .mailmap |\
    cut -c 53- |\
    grep -v -e '^$' |\
    awk '{print}' ORS='\\\\|'`--dummy thing to absorb the last delimiter--$"

mv .mailmap .mailmap.tmp

EXECPTION_COMMITS=`git log --oneline --format='%aN <%aE>' | grep  "$EXCEPTIONS" | wc -l`

mv .mailmap.tmp .mailmap

echo $EXECPTION_COMMITS exceptions

if [ $EXECPTION_COMMITS -gt 240 ]
then
    echo "You bringing commit with excepted author that is no longer allowed"
    git log --oneline --format='%aN <%aE>' | grep  "$EXCEPTIONS"
    exit 1
fi


echo "all clear"
